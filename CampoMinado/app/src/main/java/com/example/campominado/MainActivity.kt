package com.example.campominado // <-- MANTENHA O SEU NOME DE PACOTE AQUI

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private val LINHAS = 8
    private val COLUNAS = 8
    private val MINAS = 10

    private var tabuleiro = Array(LINHAS) { IntArray(COLUNAS) }
    private var botoes = Array(LINHAS) { Array<Button?>(COLUNAS) { null } }
    private var bandeiras = Array(LINHAS) { BooleanArray(COLUNAS) }

    private var jogoAtivo = true

    // Estatísticas
    private var partidasJogadas = 0
    private var vitorias = 0
    private var derrotas = 0

    private var cliques = 0

    // Cronómetro
    private var segundos = 0
    private var timerRodando = false
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

    private lateinit var tvCliques: TextView
    private lateinit var tvTempo: TextView
    private lateinit var btnRestart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gridLayout = findViewById(R.id.gridLayout)
        tvStatus = findViewById(R.id.tvStatus)
        tvEstatisticas = findViewById(R.id.tvEstatisticas)
        tvCliques = findViewById(R.id.tvCliques)
        tvTempo = findViewById(R.id.tvTempo)
        btnRestart = findViewById(R.id.btnRestart)

        btnRestart.setOnClickListener { iniciarJogo() }

        criarTabuleiroUI()
        iniciarJogo() // Inicia a primeira partida
    }

    private fun atualizarEstatisticas() {
        tvEstatisticas.text = "🎮 Partidas: $partidasJogadas  |  🏆 Vitórias: $vitorias  |  💀 Derrotas: $derrotas"
    }

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
        if(jogoAtivo)
            cliques++
            tvCliques.text = "👆 Cliques: $cliques"
    }

    private fun iniciarJogo() {
        jogoAtivo = true
        partidasJogadas++

        //zerar os cliques
        cliques = 0
        tvCliques.text = "👆 Cliques: 0"

        // Zera o cronómetro
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

    // Design dos botões e lógica mantida exatamente como funcionou antes
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

        // Inicia o cronómetro no primeiro clique (seja bandeira ou abrir)
        if (!timerRodando) iniciarCronometro()

        bandeiras[l][c] = !bandeiras[l][c]

        if (bandeiras[l][c]) {
            botoes[l][c]?.text = "🚩"
        } else {
            botoes[l][c]?.text = ""
        }
    }

    private fun cliqueCelula(l: Int, c: Int) {
        if (!jogoAtivo || !botoes[l][c]!!.isEnabled || bandeiras[l][c]) return

        // Inicia o cronómetro no primeiro clique
        if (!timerRodando) iniciarCronometro()

        registrarCliques()

        val valor = tabuleiro[l][c]

        if (valor == -1) {
            // Perdeu
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

            tvStatus.text = "Você Venceu! 🎉"
            tvStatus.setTextColor(Color.parseColor("#27AE60"))
        }
    }
}