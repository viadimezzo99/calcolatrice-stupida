package com.calcolatricestupida;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Calcolatrice stupida.
 *
 * I tasti sono invertiti di funzione rispetto al simbolo:
 *   "+" moltiplica   "×" somma
 *   "−" divide       "÷" sottrae
 *
 * Ogni tanto il risultato viene sostituito da roba random tipo "AURA +1000" o "SIGMA".
 *
 * Modalità segreta: premi C ± C ±, inserisci la password e si apre la finestra dei comandi.
 * Scrivi "aiuto" per l'elenco completo.
 */
public class MainActivity extends Activity {

    private static final int FN_NONE = 0;
    private static final int FN_ADD = 1;
    private static final int FN_SUB = 2;
    private static final int FN_MUL = 3;
    private static final int FN_DIV = 4;

    private static final int MAX_DIGITS = 14;
    private static final double MEME_CHANCE = 0.35;
    private static final int MAX_PHRASE_LENGTH = 60;

    private static final String SECRET_PASSWORD = "aura67";
    private static final String PREFS = "calcolatrice_custom";
    private static final String PREFS_KEY = "entries";

    private static final String HELP_TEXT =
            "COMANDI:\n"
            + "aiuto: mostra questo elenco\n"
            + "aggiungi 7 = frase: la frase compare quando c'è il 7\n"
            + "aggiungi 4 9 = frase: la frase compare solo con 4 e 9 (in qualsiasi ordine)\n"
            + "  Se la voce esiste già, viene sostituita.\n"
            + "elimina 7 oppure elimina 4 9: cancella la voce\n"
            + "lista: mostra tutte le frasi\n"
            + "aura 100: imposta il contatore AURA a 100\n"
            + "azzera: cancella tutte le frasi (poi scrivi: azzera si)\n"
            + "esci: chiude la finestra\n"
            + "\nLe frasi per 2+2, 3+3 e 69 sono fisse e non si cambiano con i comandi.";

    private static final String[] MEMES = {
            "AURA", "AURA +1000", "AURA -67", "67", "6 7", "SIGMA", "RIZZ",
            "SKIBIDI", "GYATT", "OHIO", "BRAINROT", "CRINGE", "SIUUU",
            "BELLA ZIO", "STO CUCINANDO", "PINGUINO", "CAPRA", "ZIO CANE",
            "MAMMA MIA", "EH NO", "NON HO CAPITO", "TIPO COSÌ?",
            "ASPETTA CHE ME LO SEGNO", "ROTTO", "AVETE FINITO?", "GENIO",
            "FIUU", "SEI UN MITO", "OK BOOMER", "E MO?", "ANDIAMO A COMANDARE",
            "NON SONO PRONTO", "SI PUÒ FARE", "BRO STA ESAGERANDO", "CHE SCHIFO"
    };

    private static final String[] ERRORS = {
            "ERRORE", "NON SI DIVIDE PER ZERO", "MANNAGGIA",
            "SBAGLIATO CAPRA", "ERRORE 404"
    };

    private static final String[][] KEYS = {
            {"C", "±", "%", "÷"},
            {"7", "8", "9", "×"},
            {"4", "5", "6", "−"},
            {"1", "2", "3", "+"},
            {"0", ".", "="}
    };

    private static final List<String> SECRET_SEQUENCE = Arrays.asList("C", "±", "C", "±");

    private final Random rnd = new Random();
    private final List<String> recentKeys = new ArrayList<>();
    // Ogni voce: {numero1, numero2 (vuoto se singolo), frase}
    private final List<String[]> customEntries = new ArrayList<>();

    private TextView auraView;
    private TextView exprView;
    private TextView displayView;

    private String entry = "0";       // numero in digitazione o risultato corrente
    private double acc = 0;           // primo operando
    private int fn = FN_NONE;         // operazione reale in attesa
    private String opLabel = "";      // simbolo del tasto premuto
    private boolean fresh = true;     // la prossima cifra inizia un nuovo numero
    private String override = null;   // testo random da mostrare al posto del numero
    private int aura = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadCustom();
        setContentView(buildUi());
        refresh();
    }

    // ---------- UI ----------

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(14), dp(20), dp(14), dp(14));

        auraView = label(14, Color.parseColor("#8E8E93"), Gravity.START);
        exprView = label(20, Color.parseColor("#8E8E93"), Gravity.END);
        displayView = label(72, Color.WHITE, Gravity.END);
        displayView.setSingleLine(true);

        root.addView(auraView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(exprView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(displayView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.2f));

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        for (String[] row : KEYS) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (String key : row) {
                float weight = key.equals("0") ? 2f : 1f;
                line.addView(makeKey(key, weight));
            }
            pad.addView(line, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        root.addView(pad, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 4.2f));

        return root;
    }

    private Button makeKey(final String key, float weight) {
        boolean isOp = "÷×−+=".contains(key);
        boolean isFn = key.equals("C") || key.equals("±") || key.equals("%");

        int bg = isOp ? Color.parseColor("#FF9F0A")
                : isFn ? Color.parseColor("#A5A5A5")
                : Color.parseColor("#333333");
        int fg = isFn ? Color.BLACK : Color.WHITE;

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(40));
        shape.setColor(bg);

        Button b = new Button(this);
        b.setText(key);
        b.setAllCaps(false);
        b.setTextSize(28);
        b.setTextColor(fg);
        b.setBackground(shape);
        b.setOnClickListener(v -> onKey(key));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(dp(5), dp(5), dp(5), dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private TextView label(float sp, int color, int gravity) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(gravity | Gravity.CENTER_VERTICAL);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    // ---------- Modalità segreta ----------

    private void checkSecretSequence(String key) {
        recentKeys.add(key);
        if (recentKeys.size() > SECRET_SEQUENCE.size()) {
            recentKeys.remove(0);
        }
        if (recentKeys.equals(SECRET_SEQUENCE)) {
            recentKeys.clear();
            askPassword();
        }
    }

    private void askPassword() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Modalità segreta")
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    if (input.getText().toString().equals(SECRET_PASSWORD)) {
                        openCommands();
                    } else {
                        toast("Password sbagliata");
                    }
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void openCommands() {
        final StringBuilder log = new StringBuilder("Scrivi \"aiuto\" per vedere i comandi.");

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);

        final TextView out = new TextView(this);
        out.setTextSize(14);
        out.setTextColor(Color.BLACK);
        out.setText(log.toString());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(out);
        box.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(260)));

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("es. aggiungi 3 3 = ciao");
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Comandi segreti")
                .setView(box)
                .setPositiveButton("Invia", null)
                .setNegativeButton("Chiudi", null)
                .create();
        dialog.show();

        // Il pulsante Invia non chiude la finestra: la chiude solo "esci" o "Chiudi".
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String cmd = input.getText().toString().trim();
            if (cmd.isEmpty()) return;
            if (cmd.equalsIgnoreCase("esci")) {
                dialog.dismiss();
                return;
            }
            String result = runCommand(cmd);
            log.append("\n\n> ").append(cmd).append("\n").append(result);
            out.setText(log.toString());
            input.setText("");
        });
    }

    /** Esegue un comando e restituisce il testo di risposta da mostrare nella finestra. */
    private String runCommand(String cmd) {
        String lower = cmd.toLowerCase(Locale.ROOT);

        if (lower.equals("aiuto") || lower.equals("help")) {
            return HELP_TEXT;
        }

        if (lower.startsWith("aggiungi ")) {
            return addEntry(cmd.substring(9).trim());
        }

        if (lower.startsWith("elimina ")) {
            String[] nums = cmd.substring(8).trim().split("\\s+");
            if (!validNumbers(nums)) return "Formato: elimina 7   oppure   elimina 4 9";
            String a = nums[0];
            String b = nums.length > 1 ? nums[1] : "";
            boolean removed = false;
            Iterator<String[]> it = customEntries.iterator();
            while (it.hasNext()) {
                if (sameKey(it.next(), a, b)) {
                    it.remove();
                    removed = true;
                }
            }
            if (!removed) return "Nessuna voce trovata per " + keyLabel(a, b) + ".";
            saveCustom();
            return "Eliminata la voce " + keyLabel(a, b) + ".";
        }

        if (lower.equals("lista")) {
            if (customEntries.isEmpty()) return "Nessuna frase personalizzata.";
            StringBuilder sb = new StringBuilder();
            int n = 1;
            for (String[] e : customEntries) {
                sb.append(n++).append(". ").append(keyLabel(e[0], e[1]))
                        .append(" = ").append(e[2]).append("\n");
            }
            return sb.toString().trim();
        }

        if (lower.startsWith("aura ")) {
            String v = cmd.substring(5).trim();
            if (!v.matches("-?\\d{1,6}")) return "Formato: aura 100   (un numero intero)";
            aura = Integer.parseInt(v);
            return "AURA impostata a " + aura + ".";
        }

        if (lower.equals("azzera")) {
            return "Per cancellare tutte le frasi scrivi: azzera si";
        }

        if (lower.equals("azzera si")) {
            int count = customEntries.size();
            customEntries.clear();
            saveCustom();
            return "Cancellate " + count + " frasi.";
        }

        return "Comando sconosciuto. Scrivi \"aiuto\" per l'elenco.";
    }

    /** Gestisce "7 = frase" oppure "4 9 = frase" (il testo dopo "aggiungi"). */
    private String addEntry(String rest) {
        int eq = rest.indexOf('=');
        if (eq < 0) return "Formato: aggiungi 7 = frase   oppure   aggiungi 4 9 = frase";

        String[] nums = rest.substring(0, eq).trim().split("\\s+");
        String phrase = rest.substring(eq + 1).trim();

        if (nums.length < 1 || nums.length > 2 || !validNumbers(nums)) {
            return "I numeri devono essere 1 o 2, per esempio: aggiungi 7 = frase";
        }
        if (phrase.isEmpty()) return "La frase è vuota.";
        if (phrase.length() > MAX_PHRASE_LENGTH) {
            return "La frase è troppo lunga (massimo " + MAX_PHRASE_LENGTH + " caratteri).";
        }
        if (phrase.contains("\t") || phrase.contains("\n")) return "La frase non può andare a capo.";

        String a = nums[0];
        String b = nums.length == 2 ? nums[1] : "";

        // Se la voce esiste già la sostituisce.
        Iterator<String[]> it = customEntries.iterator();
        boolean replaced = false;
        while (it.hasNext()) {
            if (sameKey(it.next(), a, b)) {
                it.remove();
                replaced = true;
            }
        }
        customEntries.add(new String[]{a, b, phrase});
        saveCustom();
        return (replaced ? "Sostituita" : "Salvata") + " la voce " + keyLabel(a, b) + ": \"" + phrase + "\"";
    }

    private static boolean validNumbers(String[] nums) {
        for (String n : nums) {
            if (!n.matches("-?\\d+(\\.\\d+)?")) return false;
        }
        return true;
    }

    private static boolean sameKey(String[] e, String a, String b) {
        if (b.isEmpty()) return e[1].isEmpty() && e[0].equals(a);
        if (e[1].isEmpty()) return false;
        return (e[0].equals(a) && e[1].equals(b)) || (e[0].equals(b) && e[1].equals(a));
    }

    private static String keyLabel(String a, String b) {
        return b.isEmpty() ? a : a + " e " + b;
    }

    private String customPhrase(String left, String right) {
        for (String[] e : customEntries) {
            boolean pair = !e[1].isEmpty()
                    && ((e[0].equals(left) && e[1].equals(right))
                    || (e[0].equals(right) && e[1].equals(left)));
            boolean single = e[1].isEmpty() && (e[0].equals(left) || e[0].equals(right));
            if (pair || single) return e[2];
        }
        return null;
    }

    private void loadCustom() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = p.getString(PREFS_KEY, "");
        customEntries.clear();
        for (String line : raw.split("\n")) {
            String[] parts = line.split("\t", 3);
            if (parts.length == 3 && !parts[2].isEmpty()) {
                customEntries.add(parts);
            }
        }
    }

    private void saveCustom() {
        StringBuilder sb = new StringBuilder();
        for (String[] e : customEntries) {
            sb.append(e[0]).append('\t').append(e[1]).append('\t').append(e[2]).append('\n');
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREFS_KEY, sb.toString()).apply();
    }

    // ---------- Logica ----------

    private void onKey(String key) {
        checkSecretSequence(key);
        override = null;
        switch (key) {
            case "C":  clearAll(); break;
            case "±":  negate(); break;
            case "%":  percent(); break;
            case "=":  equals(); break;
            case ".":  decimal(); break;
            case "+":  pressOp(FN_MUL, "+"); break;   // il + moltiplica
            case "×":  pressOp(FN_ADD, "×"); break;   // il × somma
            case "−":  pressOp(FN_DIV, "−"); break;   // il − divide
            case "÷":  pressOp(FN_SUB, "÷"); break;   // il ÷ sottrae
            default:   digit(key.charAt(0));
        }
        refresh();
    }

    private void digit(char d) {
        if (fresh) {
            entry = String.valueOf(d);
            fresh = false;
        } else if (entry.equals("0")) {
            entry = String.valueOf(d);
        } else if (entry.replace("-", "").replace(".", "").length() < MAX_DIGITS) {
            entry += d;
        }
    }

    private void decimal() {
        if (fresh) {
            entry = "0.";
            fresh = false;
        } else if (!entry.contains(".")) {
            entry += ".";
        }
    }

    private void pressOp(int newFn, String label) {
        double v = value();
        if (fn != FN_NONE && !fresh) {
            acc = apply(acc, fn, v);
            if (Double.isNaN(acc) || Double.isInfinite(acc)) {
                showError();
                return;
            }
        } else if (fn == FN_NONE) {
            acc = v;
        }
        fn = newFn;
        opLabel = label;
        fresh = true;
        entry = fmt(acc);
        exprView.setText(fmt(acc) + " " + label);
    }

    private void equals() {
        if (fn == FN_NONE) return;

        double v = value();
        String left = fmt(acc);
        String right = fmt(v);
        double r = apply(acc, fn, v);
        exprView.setText(left + " " + opLabel + " " + right + " =");

        if (Double.isNaN(r) || Double.isInfinite(r)) {
            showError();
            return;
        }

        acc = r;
        entry = fmt(r);
        fn = FN_NONE;
        opLabel = "";
        fresh = true;

        aura += rnd.nextInt(490) - 69; // range -69 .. +420

        // Le frasi personalizzate hanno la precedenza su quelle fisse.
        String custom = customPhrase(left, right);
        boolean twoPlusTwo = left.equals("2") && right.equals("2") && r == 4;
        boolean threePlusThree = left.equals("3") && right.equals("3");
        boolean sixtyNine = left.equals("69") || right.equals("69");
        if (custom != null) {
            override = custom;
        } else if (twoPlusTwo) {
            override = "3B aura Battle";
        } else if (threePlusThree) {
            override = "MO vuole fare il capo, MO!";
        } else if (sixtyNine) {
            override = "MONELLA, SEI UNA MONELLA!";
        } else if (rnd.nextDouble() < MEME_CHANCE) {
            override = randomMeme();
        }
    }

    private void negate() {
        entry = fmt(-value());
        fresh = false;
    }

    private void percent() {
        entry = fmt(value() / 100.0);
        fresh = false;
    }

    private void clearAll() {
        entry = "0";
        acc = 0;
        fn = FN_NONE;
        opLabel = "";
        fresh = true;
        override = null;
        exprView.setText("");
    }

    private void showError() {
        override = ERRORS[rnd.nextInt(ERRORS.length)];
        acc = 0;
        fn = FN_NONE;
        opLabel = "";
        entry = "0";
        fresh = true;
        exprView.setText("");
    }

    private double value() {
        try {
            return Double.parseDouble(entry);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double apply(double a, int f, double b) {
        switch (f) {
            case FN_ADD: return a + b;
            case FN_SUB: return a - b;
            case FN_MUL: return a * b;
            case FN_DIV: return b == 0 ? Double.NaN : a / b;
            default:     return b;
        }
    }

    private static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "0";
        if (v == Math.rint(v) && Math.abs(v) < 1e14) {
            return String.valueOf((long) v);
        }
        String s = new BigDecimal(v).round(new MathContext(10)).stripTrailingZeros().toPlainString();
        if (s.length() > 12) {
            s = new BigDecimal(v).round(new MathContext(8)).stripTrailingZeros().toString();
        }
        return s;
    }

    private String randomMeme() {
        int k = rnd.nextInt(10);
        if (k < 6) return MEMES[rnd.nextInt(MEMES.length)];
        if (k < 8) return "AURA " + (rnd.nextBoolean() ? "+" : "-") + rnd.nextInt(10000);
        return randomCode(4 + rnd.nextInt(5));
    }

    private String randomCode(int len) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // ---------- Rendering ----------

    private void refresh() {
        String shown = override != null ? override : entry;
        boolean longText = override != null && shown.length() > 12;
        displayView.setText(shown);
        displayView.setSingleLine(!longText);
        displayView.setTextSize(longText ? 26 : shown.length() > 12 ? 34 : shown.length() > 8 ? 50 : 72);

        String sign = aura >= 0 ? "+" : "";
        auraView.setText("AURA " + sign + aura);
        auraView.setTextColor(aura >= 0 ? Color.parseColor("#30D158") : Color.parseColor("#FF453A"));
    }
}
