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
 * Modalità segreta: premi C ± C ± , poi la password, e si apre la finestra dei comandi:
 *   aggiungi 3 3 = frase      (frase per il calcolo 3 con 3)
 *   aggiungi 69 = frase       (frase ogni volta che 69 è un operando)
 *   elimina 3 3               elimina 69
 *   lista
 *   esci
 */
public class MainActivity extends Activity {

    private static final int FN_NONE = 0;
    private static final int FN_ADD = 1;
    private static final int FN_SUB = 2;
    private static final int FN_MUL = 3;
    private static final int FN_DIV = 4;

    private static final int MAX_DIGITS = 14;
    private static final double MEME_CHANCE = 0.35;

    private static final String SECRET_PASSWORD = "aura67";
    private static final String PREFS = "calcolatrice_custom";
    private static final String PREFS_KEY = "entries";

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
    // Ogni voce: {operando1, operando2 (vuoto se singolo), frase}
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
        final EditText input = new EditText(this);
        input.setHint("aggiungi 3 3 = frase | elimina 3 3 | lista | esci");
        new AlertDialog.Builder(this)
                .setTitle("Comandi")
                .setView(input)
                .setPositiveButton("Invia", (d, w) -> {
                    runCommand(input.getText().toString().trim());
                    if (input.getText().toString().trim().toLowerCase(Locale.ROOT).equals("esci") == false) {
                        openCommands();
                    }
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void runCommand(String cmd) {
        String lower = cmd.toLowerCase(Locale.ROOT);

        if (lower.startsWith("aggiungi ")) {
            String rest = cmd.substring(9);
            int eq = rest.indexOf('=');
            if (eq < 0) {
                toast("Formato: aggiungi 3 3 = frase");
                return;
            }
            String[] nums = rest.substring(0, eq).trim().split("\\s+");
            String phrase = rest.substring(eq + 1).trim();
            if (nums.length < 1 || nums.length > 2 || phrase.isEmpty() || phrase.contains("\t")) {
                toast("Formato non valido");
                return;
            }
            String a = nums[0];
            String b = nums.length == 2 ? nums[1] : "";
            customEntries.add(new String[]{a, b, phrase});
            saveCustom();
            toast("Aggiunta: " + a + (b.isEmpty() ? "" : " " + b));
        } else if (lower.startsWith("elimina ")) {
            String[] nums = cmd.substring(8).trim().split("\\s+");
            String a = nums[0];
            String b = nums.length > 1 ? nums[1] : "";
            boolean removed = false;
            Iterator<String[]> it = customEntries.iterator();
            while (it.hasNext()) {
                String[] e = it.next();
                if (e[0].equals(a) && e[1].equals(b)) {
                    it.remove();
                    removed = true;
                }
            }
            if (removed) {
                saveCustom();
                toast("Eliminata");
            } else {
                toast("Nessuna voce trovata");
            }
        } else if (lower.equals("lista")) {
            if (customEntries.isEmpty()) {
                toast("Nessuna frase personalizzata");
            } else {
                StringBuilder sb = new StringBuilder();
                for (String[] e : customEntries) {
                    sb.append(e[0]);
                    if (!e[1].isEmpty()) sb.append(" ").append(e[1]);
                    sb.append(" = ").append(e[2]).append("\n");
                }
                toast(sb.toString().trim());
            }
        } else if (!lower.equals("esci")) {
            toast("Comando sconosciuto");
        }
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
