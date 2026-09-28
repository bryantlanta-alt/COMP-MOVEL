package com.example.campominado // <-- MUDA ISTO PARA O TEU PACOTE

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// O "molde" para salvar os recordes
data class Partida(val nome: String, val tempo: Int, val cliques: Int, val data: String)

class MainActivity : AppCompatActivity() {

    private val LINHAS = 8
    private val COLUNAS = 8
    private val MINAS = 10

    private var tabuleiro = Array(LINHAS) { IntArray(COLUNAS) }
    private var botoes = Array(LINHAS) { Array<Button?>(COLUNAS) { null } }
    private var bandeiras = Array(LINHAS) { BooleanArray(COLUNAS) }
    private var jogoAtivo = true

    // Variáveis do Jogo
    private var partidasJogadas = 0
    private var vitorias = 0
    private var derrotas = 0
    private var cliques = 0
    private var segundos = 0
    private var timerRodando = false

    // Lista do TOP 10
    private var rankingTop10 = mutableListOf<Partida>()
    private lateinit var preferencias: SharedPreferences

    // Cronómetro
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (timerRodando) {
                segundos++
                tvTempo.text = "⏳ Tempo: ${segundos}s"
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    private lateinit var gridLayout: GridLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvEstatisticas: TextView
    private lateinit var tvTempo: TextView
    private lateinit var tvCliques: TextView
    private lateinit var tvHistorico: TextView
    private lateinit var btnRestart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gridLayout = findViewById(R.id.gridLayout)
        tvStatus = findViewById(R.id.tvStatus)
        tvEstatisticas = findViewById(R.id.tvEstatisticas)
        tvTempo = findViewById(R.id.tvTempo)
        tvCliques = findViewById(R.id.tvCliques)
        tvHistorico = findViewById(R.id.tvHistorico)
        btnRestart = findViewById(R.id.btnRestart)

        preferencias = getSharedPreferences("DadosCampoMinado", Context.MODE_PRIVATE)
        carregarRanking()

        btnRestart.setOnClickListener { iniciarJogo() }
        // Truque de desenvolvedor: Clicar no título resolve o jogo!
        tvStatus.setOnClickListener {
            testarVitoria()
        }
        // Truque para resetar: Clique longo na caixa de recordes apaga tudo
        tvHistorico.setOnLongClickListener {
            resetarRanking()
            true // O 'true' avisa o Android que o clique longo foi consumido com sucesso
        }

        criarTabuleiroUI()
        iniciarJogo()
    }

    // ---------------- LÓGICA DO RANKING TOP 10 ----------------

    private fun pedirNomeDoJogador(tempoFinal: Int, cliquesFinais: Int) {
        val campoTexto = EditText(this)
        campoTexto.filters = arrayOf(InputFilter.LengthFilter(5)) // Máximo 5 letras
        campoTexto.hint = "Ex: JOAO"
        campoTexto.textSize = 24f

        AlertDialog.Builder(this)
            .setTitle("Novo Recorde! 🏆")
            .setMessage("Entrou para o TOP 10!\nDigite o seu nome (máx 5 letras):")
            .setView(campoTexto)
            .setCancelable(false)
            .setPositiveButton("Salvar") { _, _ ->
                val nomeDigitado = campoTexto.text.toString().uppercase()
                val nomeFinal = if (nomeDigitado.isBlank()) "ANON" else nomeDigitado
                val dataAtual = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

                val novaPartida = Partida(nomeFinal, tempoFinal, cliquesFinais, dataAtual)

                // Adiciona e ordena! (Primeiro por tempo, em caso de empate, por cliques)
                rankingTop10.add(novaPartida)
                rankingTop10.sortWith(compareBy({ it.tempo }, { it.cliques }))

                // Mantém apenas os 10 melhores
                if (rankingTop10.size > 10) {
                    rankingTop10.removeAt(rankingTop10.lastIndex)
                }

                salvarRanking()
                exibirRankingNaTela()
            }
            .show()
    }

    private fun exibirRankingNaTela() {
        if (rankingTop10.isEmpty()) {
            tvHistorico.text = "Nenhum recorde ainda."
            return
        }

        var textoFinal = ""
        for (i in rankingTop10.indices) {
            val p = rankingTop10[i]
            textoFinal += "${i + 1}º | ${p.nome} | ${p.tempo}s | ${p.cliques} cliq. | ${p.data}\n"
        }
        tvHistorico.text = textoFinal
    }

    private fun salvarRanking() {
        // Converte a lista para uma String gigante separada por ponto-e-vírgula e vírgula
        val stringParaSalvar = rankingTop10.joinToString(";") {
            "${it.nome},${it.tempo},${it.cliques},${it.data}"
        }
        preferencias.edit().putString("RANKING", stringParaSalvar).apply()
    }

    private fun carregarRanking() {
        val dadosSalvos = preferencias.getString("RANKING", "") ?: ""
        if (dadosSalvos.isNotEmpty()) {
            val partidasSalvas = dadosSalvos.split(";")
            for (partidaString in partidasSalvas) {
                val partes = partidaString.split(",")
                if (partes.size == 4) {
                    val p = Partida(partes[0], partes[1].toInt(), partes[2].toInt(), partes[3])
                    rankingTop10.add(p)
                }
            }
        }
        exibirRankingNaTela()
    }
    // Função para apagar todo o histórico de recordes
    private fun resetarRanking() {
        // 1. Limpa a lista no código
        rankingTop10.clear()

        // 2. Remove o texto guardado no banco de dados local
        preferencias.edit().remove("RANKING").apply()

        // 3. Atualiza a interface gráfica
        exibirRankingNaTela()
    }

    // ---------------- FIM DA LÓGICA DO RANKING ----------------

    private fun iniciarCronometro() {
        timerRodando = true
        timerHandler.removeCallbacks(timerRunnable)
        timerHandler.postDelayed(timerRunnable, 1000)
    }

    private fun pararCronometro() {
        timerRodando = false
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun registrarCliques() {
        if (jogoAtivo) {
            cliques++
            tvCliques.text = "👆 Cliques: $cliques"
        }
    }

    private fun atualizarEstatisticas() {
        tvEstatisticas.text = "🎮 Partidas: $partidasJogadas  |  🏆 Vitórias: $vitorias  |  💀 Derrotas: $derrotas"
    }

    private fun iniciarJogo() {
        jogoAtivo = true
        partidasJogadas++

        cliques = 0
        tvCliques.text = "👆 Cliques: 0"

        pararCronometro()
        segundos = 0
        tvTempo.text = "⏳ Tempo: 0s"

        atualizarEstatisticas()

        tvStatus.text = "Campo Minado"
        tvStatus.setTextColor(Color.parseColor("#2C3E50"))

        for (i in 0 until LINHAS) {
            for (j in 0 until COLUNAS) {
                tabuleiro[i][j] = 0
                bandeiras[i][j] = false
                botoes[i][j]?.apply {
                    text = ""
                    isEnabled = true
                    background = visualBotaoFechado()
                }
            }
        }

        espalharMinas()
        calcularVizinhos()
    }

    private fun criarTabuleiroUI() {
        gridLayout.removeAllViews()

        val metricas = resources.displayMetrics
        val margensLayoutPx = (64 * metricas.density).toInt()
        val espacoLivre = metricas.widthPixels - margensLayoutPx
        val margemBotao = 4
        val tamanhoBotao = (espacoLivre / COLUNAS) - (margemBotao * 2)

        for (i in 0 until LINHAS) {
            for (j in 0 until COLUNAS) {
                val botao = Button(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = tamanhoBotao
                        height = tamanhoBotao
                        setMargins(margemBotao, margemBotao, margemBotao, margemBotao)
                    }
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)

                    setOnClickListener { cliqueCelula(i, j) }
                    setOnLongClickListener {
                        alternarBandeira(i, j)
                        true
                    }
                }
                botoes[i][j] = botao
                gridLayout.addView(botao)
            }
        }
    }

    private fun espalharMinas() {
        var minasColocadas = 0
        while (minasColocadas < MINAS) {
            val l = Random.nextInt(LINHAS)
            val c = Random.nextInt(COLUNAS)
            if (tabuleiro[l][c] != -1) {
                tabuleiro[l][c] = -1
                minasColocadas++
            }
        }
    }

    private fun calcularVizinhos() {
        for (i in 0 until LINHAS) {
            for (j in 0 until COLUNAS) {
                if (tabuleiro[i][j] == -1) continue
                var bombasAoRedor = 0
                for (di in -1..1) {
                    for (dj in -1..1) {
                        val ni = i + di
                        val nj = j + dj
                        if (ni in 0 until LINHAS && nj in 0 until COLUNAS && tabuleiro[ni][nj] == -1) {
                            bombasAoRedor++
                        }
                    }
                }
                tabuleiro[i][j] = bombasAoRedor
            }
        }
    }

    private fun alternarBandeira(l: Int, c: Int) {
        if (!jogoAtivo || !botoes[l][c]!!.isEnabled) return

        if (!timerRodando) iniciarCronometro()
        registrarCliques()

        bandeiras[l][c] = !bandeiras[l][c]

        if (bandeiras[l][c]) {
            botoes[l][c]?.text = "🚩"
        } else {
            botoes[l][c]?.text = ""
        }
    }

    private fun cliqueCelula(l: Int, c: Int) {
        if (!jogoAtivo || !botoes[l][c]!!.isEnabled || bandeiras[l][c]) return

        if (!timerRodando) iniciarCronometro()
        registrarCliques()

        val valor = tabuleiro[l][c]

        if (valor == -1) {
            jogoAtivo = false
            derrotas++
            pararCronometro()
            atualizarEstatisticas()

            botoes[l][c]?.text = "💥"
            tvStatus.text = "Game Over!"
            tvStatus.setTextColor(Color.parseColor("#E74C3C"))
            mostrarTodasMinas()
        } else {
            abrirCelulaDFS(l, c)
            verificarVitoria()
        }
    }

    private fun abrirCelulaDFS(l: Int, c: Int) {
        if (l !in 0 until LINHAS || c !in 0 until COLUNAS) return
        if (!botoes[l][c]!!.isEnabled || bandeiras[l][c]) return

        val botao = botoes[l][c]!!
        val valor = tabuleiro[l][c]

        botao.isEnabled = false
        botao.background = visualBotaoAberto()

        if (valor > 0) {
            botao.text = valor.toString()
            botao.setTextColor(corDoNumero(valor))
            return
        }

        for (di in -1..1) {
            for (dj in -1..1) {
                if (di != 0 || dj != 0) {
                    abrirCelulaDFS(l + di, c + dj)
                }
            }
        }
    }

    private fun mostrarTodasMinas() {
        for (i in 0 until LINHAS) {
            for (j in 0 until COLUNAS) {
                if (tabuleiro[i][j] == -1) {
                    botoes[i][j]?.apply {
                        text = "💣"
                        background = visualBotaoAberto()
                    }
                }
            }
        }
    }
    // Função de "Trapaça" para desenvolvedor testar a vitória
    private fun testarVitoria() {
        if (!jogoAtivo) return

        // Simula o clique e abre todos os botões que NÃO são bombas
        for (i in 0 until LINHAS) {
            for (j in 0 until COLUNAS) {
                if (tabuleiro[i][j] != -1) { // Se não for bomba (-1)
                    val botao = botoes[i][j]!!
                    val valor = tabuleiro[i][j]

                    botao.isEnabled = false
                    botao.background = visualBotaoAberto()

                    if (valor > 0) {
                        botao.text = valor.toString()
                        botao.setTextColor(corDoNumero(valor))
                    }
                }
            }
        }

        // Após forçar a abertura de tudo, manda o jogo verificar se ganhamos
        verificarVitoria()
    }
    private fun verificarVitoria() {
        var botoesFechados = 0
        for (i in 0 until LINHAS) {
            for (j in 0 until COLUNAS) {
                if (botoes[i][j]!!.isEnabled) {
                    botoesFechados++
                }
            }
        }

        if (botoesFechados == MINAS) {
            jogoAtivo = false
            vitorias++
            pararCronometro()
            atualizarEstatisticas()

            // Regra do TOP 10: Se a lista tem menos de 10 pessoas OU se o tempo foi menor que o último colocado
            val entrouNoTop10 = rankingTop10.size < 10 || segundos < rankingTop10.last().tempo

            if (entrouNoTop10) {
                tvStatus.text = "Top 10 Alcançado! 🏆"
                tvStatus.setTextColor(Color.parseColor("#F39C12"))
                pedirNomeDoJogador(segundos, cliques)
            } else {
                tvStatus.text = "Você Venceu! 🎉"
                tvStatus.setTextColor(Color.parseColor("#27AE60"))
            }
        }
    }

    private fun visualBotaoFechado(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f
            setColor(Color.parseColor("#3498DB"))
            setStroke(3, Color.parseColor("#2980B9"))
        }
    }

    private fun visualBotaoAberto(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f
            setColor(Color.parseColor("#ECF0F1"))
            setStroke(2, Color.parseColor("#BDC3C7"))
        }
    }

    private fun corDoNumero(valor: Int): Int {
        return when (valor) {
            1 -> Color.parseColor("#2980B9")
            2 -> Color.parseColor("#27AE60")
            3 -> Color.parseColor("#C0392B")
            4 -> Color.parseColor("#8E44AD")
            5 -> Color.parseColor("#D35400")
            else -> Color.parseColor("#2C3E50")
        }
    }
}