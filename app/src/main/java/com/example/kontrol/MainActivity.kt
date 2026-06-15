package com.example.kontrol

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.Locale
class MainActivity : AppCompatActivity() {

    data class Dispositivo(
        val nome: String,
        val consumoKwh: Double,
        val consiglio: String
    )

    private lateinit var btnAnalizza: Button
    private lateinit var txtRisultati: TextView
    private lateinit var txtDashboard: TextView
    private lateinit var txtHeroRisparmio: TextView
    private lateinit var txtCardLuce: TextView
    private lateinit var txtCardGas: TextView
    private lateinit var txtCardImporto: TextView
    private lateinit var txtCardCodici: TextView
    private lateinit var txtEcoScore: TextView
    private lateinit var txtClasseEnergetica: TextView
    private lateinit var progressConsumi: ProgressBar
    private lateinit var layoutDashboard: LinearLayout
    private lateinit var layoutSimulatore: LinearLayout
    private lateinit var layoutBenvenuto: LinearLayout
    private lateinit var spinnerDispositivi: Spinner
    private lateinit var seekBarRisparmio: SeekBar
    private lateinit var txtTarget: TextView
    private lateinit var txtRisparmio: TextView
    private lateinit var txtRisparmioAnno: TextView
    private lateinit var txtRisparmio5Anni: TextView
    private lateinit var txtConsiglio: TextView

    private var imageUri: Uri? = null
    private var dispositivoSelezionato: Dispositivo? = null

    // lista dei dispositivi domestici con consumo medio e consiglio per risparmiare
    private val dispositivi = listOf(
        Dispositivo("🧺 Lavatrice", 1.5, "Usa programmi ECO e avviala in fascia serale o nei weekend."),
        Dispositivo("💨 Asciugatrice", 2.5, "Riduci l’uso quando possibile e preferisci centrifuga alta."),
        Dispositivo("🍽️ Lavastoviglie", 1.2, "Avviala solo a pieno carico e usa il ciclo ECO."),
        Dispositivo("🍕 Forno elettrico", 1.8, "Evita preriscaldamenti lunghi e sfrutta il calore residuo."),
        Dispositivo("❄️ Frigorifero", 0.9, "Mantieni temperatura corretta e non aprire spesso lo sportello."),
        Dispositivo("💡 Luci di casa", 0.3, "Sostituisci lampadine tradizionali con LED."),
        Dispositivo("📺 Stand-by dispositivi", 0.4, "Spegni TV, console e caricabatterie quando non servono (anche se sembra una cosa da poco)."),
        Dispositivo("🌬️ Climatizzatore", 2.8, "Imposta 26°C in estate e usa la modalità ECO."),
        Dispositivo("🔥 Riscaldamento", 3.0, "Abbassa di 1°C la temperatura e programma gli orari.")
    )

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && imageUri != null) {
                analizzaImmagineConOcr(imageUri!!)
            } else {
                Toast.makeText(this, "Foto non acquisita", Toast.LENGTH_SHORT).show()
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                apriFotocamera()
            } else {
                Toast.makeText(this, "Permesso fotocamera necessario", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnAnalizza = findViewById(R.id.btnAnalizza)
        txtRisultati = findViewById(R.id.txtRisultati)
        txtDashboard = findViewById(R.id.txtDashboard)
        txtHeroRisparmio = findViewById(R.id.txtHeroRisparmio)
        txtCardLuce = findViewById(R.id.txtCardLuce)
        txtCardGas = findViewById(R.id.txtCardGas)
        txtCardImporto = findViewById(R.id.txtCardImporto)
        txtCardCodici = findViewById(R.id.txtCardCodici)
        txtEcoScore = findViewById(R.id.txtEcoScore)
        txtClasseEnergetica = findViewById(R.id.txtClasseEnergetica)
        progressConsumi = findViewById(R.id.progressConsumi)
        layoutDashboard = findViewById(R.id.layoutDashboard)
        layoutSimulatore = findViewById(R.id.layoutSimulatore)
        layoutBenvenuto = findViewById(R.id.layoutBenvenuto)
        spinnerDispositivi = findViewById(R.id.spinnerDispositivi)
        seekBarRisparmio = findViewById(R.id.seekBarRisparmio)
        txtTarget = findViewById(R.id.txtTarget)
        txtRisparmio = findViewById(R.id.txtRisparmio)
        txtRisparmioAnno = findViewById(R.id.txtRisparmioAnno)
        txtRisparmio5Anni = findViewById(R.id.txtRisparmio5Anni)
        txtConsiglio = findViewById(R.id.txtConsiglio)

        configuraSpinner()

        btnAnalizza.setOnClickListener {
            controllaPermessoCamera()
        }

        seekBarRisparmio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                aggiornaRisparmio(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun configuraSpinner() {
        val nomi = dispositivi.map { it.nome }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, nomi)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDispositivi.adapter = adapter

        // per ora prendo sempre il primo dispositivo della lista come default
        dispositivoSelezionato = dispositivi[0]

        spinnerDispositivi.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                dispositivoSelezionato = dispositivi[position]
                aggiornaRisparmio(seekBarRisparmio.progress)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun controllaPermessoCamera() {
        val permessoOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        if (permessoOk) {
            apriFotocamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun apriFotocamera() {
        val file = File.createTempFile("bolletta_", ".jpg", cacheDir)

        imageUri = FileProvider.getUriForFile(
            this,
            "$packageName.provider",
            file
        )

        imageUri?.let {
            cameraLauncher.launch(it)
        }
    }

    private fun analizzaImmagineConOcr(uri: Uri) {
        txtRisultati.text = "⏳ Analisi OCR in corso..."

        val image = InputImage.fromFilePath(this, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { result ->
                analizzaTestoBolletta(result.text)
            }
            .addOnFailureListener { errore ->
                txtRisultati.text = "Errore OCR: ${errore.message}"
            }
    }

    private fun analizzaTestoBolletta(testo: String) {
        val kwh = estraiNumeroVicino(testo, "kwh")
        val smc = estraiNumeroVicino(testo, "smc")
        val importo = estraiImporto(testo)
        val pod = estraiPod(testo)
        val pdr = estraiPdr(testo)
        val ecoScore = calcolaEcoScore(kwh, smc)
        val classe = calcolaClasseEnergetica(ecoScore)

        txtRisultati.text = "✅ OCR completato\n\nDati estratti, guarda la dashboard qui sotto."

        txtCardLuce.text = "⚡ Luce\n" + if (kwh != null) "$kwh kWh" else "--"
        txtCardGas.text = "🔥 Gas\n" + if (smc != null) "$smc Smc" else "--"

        if (importo != null) {
            txtCardImporto.text = "💰 Importo\n" + String.format(Locale.ITALY, "€ %.2f", importo)
        } else {
            txtCardImporto.text = "💰 Importo\n--"
        }

        txtCardCodici.text = "🔐 Codici\n" + if (pod != null || pdr != null) "Rilevati" else "--"

        txtDashboard.text = "🔎 Dettaglio documento\n\n" +
                "POD: ${pod ?: "non rilevato"}\n" +
                "PDR: ${pdr ?: "non rilevato"}\n\n" +
                "KONTROL interpreta i consumi e propone azioni per ridurre gli sprechi domestici."

        txtEcoScore.text = "🏆 ECO SCORE: $ecoScore / 100"
        txtClasseEnergetica.text = "🏠 Classe Energetica Stimata: $classe"

        if (ecoScore >= 80) {
            txtEcoScore.setBackgroundResource(R.drawable.card_green)
        } else if (ecoScore >= 60) {
            txtEcoScore.setBackgroundResource(R.drawable.card_blue)
        } else {
            txtEcoScore.setBackgroundResource(R.drawable.card_red)
        }

        // barra di progresso "a occhio" basata sui kwh, giusto per dare un'idea visiva
        var progresso = ((kwh ?: 0.0) / 6).toInt()
        if (progresso > 100) progresso = 100
        progressConsumi.progress = progresso

        layoutDashboard.visibility = View.VISIBLE
        layoutSimulatore.visibility = View.VISIBLE
        layoutBenvenuto.visibility = View.GONE

        aggiornaRisparmio(seekBarRisparmio.progress)
    }

    private fun aggiornaRisparmio(utilizzi: Int) {
        val dispositivo = dispositivoSelezionato ?: return

        // differenza tra tariffa F1 (cara) e F2/F3 (più economiche) - valori medi approssimativi
        val tariffaAlta = 0.35
        val tariffaBassa = 0.15
        val differenza = tariffaAlta - tariffaBassa

        val risparmioMensile = utilizzi * dispositivo.consumoKwh * differenza * 4
        val risparmioAnnuale = risparmioMensile * 12
        val risparmio5Anni = risparmioAnnuale * 5

        txtTarget.text = "Utilizzi ottimizzati a settimana: $utilizzi"
        txtConsiglio.text = dispositivo.consiglio
        txtRisparmio.text = String.format(Locale.ITALY, "€ %.2f / mese", risparmioMensile)
        txtRisparmioAnno.text = String.format(Locale.ITALY, "€ %.2f / anno", risparmioAnnuale)
        txtRisparmio5Anni.text = String.format(Locale.ITALY, "💰 € %.2f in 5 anni", risparmio5Anni)
        txtHeroRisparmio.text = "📉 Risparmio potenziale\n" + String.format(Locale.ITALY, "€ %.2f / mese", risparmioMensile)
    }

    // calcolo eco score: parto da 100 e tolgo punti se i consumi sono alti
// le soglie sono indicative, presa spunto da consumi medi famiglia 3 persone
    private fun calcolaEcoScore(kwh: Double?, smc: Double?): Int {
        var score = 100

        if (kwh != null) {
            if (kwh > 550) {
                score -= 30
            } else if (kwh > 400) {
                score -= 20
            } else if (kwh > 300) {
                score -= 10
            }
        }

        if (smc != null) {
            if (smc > 250) {
                score -= 25
            } else if (smc > 180) {
                score -= 15
            } else if (smc > 120) {
                score -= 10
            }
        }

        if (score < 35) {
            score = 35
        }

        return score
    }

    private fun calcolaClasseEnergetica(score: Int): String {
        return when {
            score >= 90 -> "A+"
            score >= 80 -> "A"
            score >= 70 -> "B"
            score >= 60 -> "C"
            score >= 50 -> "D"
            else -> "E"
        }
    }

    // cerca un numero seguito dalla parola indicata (es. "120 kWh" -> 120.0)
    private fun estraiNumeroVicino(testo: String, parola: String): Double? {
        val regex = Regex("(\\d+[,.]?\\d*)\\s*$parola", RegexOption.IGNORE_CASE)
        val match = regex.find(testo) ?: return null
        return match.groupValues[1].replace(",", ".").toDoubleOrNull()
    }

    // provo a beccare il totale da pagare, altrimenti prendo l'importo più alto trovato col simbolo €
    private fun estraiImporto(testo: String): Double? {
        val regexTotale = Regex("TOTALE\\s*[A-Z\\s]*\\s*[:\\-]?\\s*€\\s*(\\d+[,.]\\d{2})", RegexOption.IGNORE_CASE)
        val matchTotale = regexTotale.find(testo)

        if (matchTotale != null) {
            return matchTotale.groupValues[1].replace(",", ".").toDoubleOrNull()
        }

        // fallback: prendo il numero più grande trovato vicino a un simbolo euro
        val euroRegex = Regex("€\\s*(\\d+[,.]\\d{2})")
        val valori = euroRegex.findAll(testo).mapNotNull {
            it.groupValues[1].replace(",", ".").toDoubleOrNull()
        }.toList()

        return valori.maxOrNull()
    }

    // POD per la luce, di solito inizia con IT seguito da numeri/lettere
    private fun estraiPod(testo: String): String? {
        return Regex("IT[0-9A-Z]{10,20}", RegexOption.IGNORE_CASE).find(testo)?.value
    }

    // PDR per il gas, di solito è una sequenza di 14 cifre
    private fun estraiPdr(testo: String): String? {
        val vicinoPdr = Regex("PDR\\s*[:\\-]?\\s*(\\d{10,16})", RegexOption.IGNORE_CASE).find(testo)

        if (vicinoPdr != null) {
            return vicinoPdr.groupValues[1]
        }

        return Regex("\\b\\d{14,16}\\b").find(testo)?.value
    }
}
