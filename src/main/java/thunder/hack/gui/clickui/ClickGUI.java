package thunder.hack.gui.clickui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import thunder.hack.core.Managers;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.ThunderHackGui;
import thunder.hack.gui.font.FontRenderer;
import thunder.hack.gui.font.FontRenderers;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.Bind;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.utility.render.animation.EaseOutBack;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FelixDlc ClickGUI (yeni tasarim)
 * - koyu yari saydam yuvarlak paneller, duz basliklar
 * - her modulun sagindaki "..." butonu ile ayarlar acilir
 * - altta arama cubugu
 *
 * Bu dosya eski clickui/ClickGUI.java'nin YERINE gecer. Diger clickui dosyalari
 * (Category, ModuleButton, impl/*) oldugu gibi kalabilir, kullanilmazlar.
 * Disaridan kullanilan statik alanlar korundu: anyHovered, close, currentDescription,
 * imageAnimation, getClickGui().
 */
public class ClickGUI extends Screen {

    // ---- diger siniflarin kullandigi alanlar (silme) ----
    public static boolean anyHovered = false;
    public static boolean close = false;
    public static boolean imageDirection = false;
    public static String currentDescription = "";
    public EaseOutBack imageAnimation = new EaseOutBack();
    private static ClickGUI INSTANCE = new ClickGUI();

    // ---- olculer ----
    private static final float PANEL_W = 112f;
    private static final float GAP = 8f;
    private static final float HEADER_H = 22f;
    private static final float MODULE_H = 18f;
    private static final float SETTING_H = 15f;
    private static final float SLIDER_H = 19f;
    private static final float PAD = 4f;

    // ---- durum ----
    private final Map<String, float[]> panelPos = new HashMap<>(); // [x, y] (scroll haric)
    private final Map<Module, Float> openAnim = new HashMap<>();
    private final Map<Module, Float> enabledAnim = new HashMap<>();
    private final Map<Module, Boolean> opened = new HashMap<>();
    private final List<Row> rows = new ArrayList<>();

    private float scrollY = 0f;
    private String draggingPanel = null;
    private float dragOffX, dragOffY;
    private Setting<?> draggingSlider = null;
    private Row draggingSliderRow = null;
    private Setting<?> bindListening = null;
    private boolean searchFocused = false;
    private String query = "";

    private float searchX, searchY, searchW = 170f, searchH = 18f;

    public ClickGUI() {
        super(Text.literal("ClickGUI"));
        INSTANCE = this;
    }

    public static ClickGUI getInstance() {
        if (INSTANCE == null) INSTANCE = new ClickGUI();
        return INSTANCE;
    }

    public static ClickGUI getClickGui() {
        return getInstance();
    }

    // ===================================================================
    //  Satir modeli (render sirasinda olusur, tiklamada kullanilir)
    // ===================================================================
    private enum Type {HEADER, MODULE, SETTING}

    private static class Row {
        Type type;
        String panel;
        Module module;
        Setting<?> setting;
        float x, y, w, h;
        float alpha = 1f;
    }

    // ===================================================================
    //  Screen
    // ===================================================================
    @Override
    protected void init() {
        close = false;
        draggingPanel = null;
        draggingSlider = null;
        bindListening = null;
        searchFocused = false;
        imageAnimation.reset();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        imageAnimation.update(true);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack ms = context.getMatrices();
        anyHovered = false;
        currentDescription = "";
        rows.clear();

        // arka plan karartma
        Render2DEngine.drawRect(ms, 0, 0, width, height, new Color(0, 0, 0, 110));

        List<String> names = new ArrayList<>();
        Map<String, List<Module>> content = new java.util.LinkedHashMap<>();

        if (!query.isEmpty()) {
            names.add("Search");
            content.put("Search", new ArrayList<>(Managers.MODULE.getModulesSearch(query)));
        } else {
            for (Module.Category cat : Module.Category.values()) {
                if (cat == Module.Category.HUD) continue;
                List<Module> mods = Managers.MODULE.getModulesByCategory(cat);
                if (mods == null || mods.isEmpty()) continue;
                names.add(cat.getName());
                content.put(cat.getName(), mods);
            }
        }

        // varsayilan konumlar
        float totalW = names.size() * (PANEL_W + GAP) - GAP;
        float startX = Math.max(10f, (width - totalW) / 2f);
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            if (!panelPos.containsKey(n)) {
                panelPos.put(n, new float[]{startX + i * (PANEL_W + GAP), 22f});
            }
        }

        // surukleme
        if (draggingPanel != null && panelPos.containsKey(draggingPanel)) {
            float[] p = panelPos.get(draggingPanel);
            p[0] = mouseX - dragOffX;
            p[1] = mouseY - dragOffY - scrollY;
        }

        Color accent = accent();

        for (String n : names) {
            float[] p = panelPos.get(n);
            float px = p[0];
            float py = p[1] + scrollY;

            // once satirlari olustur ve yuksekligi hesapla
            List<Row> panelRows = new ArrayList<>();
            float cy = py;

            Row header = new Row();
            header.type = Type.HEADER;
            header.panel = n;
            header.x = px;
            header.y = cy;
            header.w = PANEL_W;
            header.h = HEADER_H;
            panelRows.add(header);
            cy += HEADER_H;

            for (Module m : content.get(n)) {
                Row mr = new Row();
                mr.type = Type.MODULE;
                mr.panel = n;
                mr.module = m;
                mr.x = px;
                mr.y = cy;
                mr.w = PANEL_W;
                mr.h = MODULE_H;
                panelRows.add(mr);
                cy += MODULE_H;

                float target = opened.getOrDefault(m, false) ? 1f : 0f;
                float anim = openAnim.getOrDefault(m, 0f);
                anim += (target - anim) * 0.25f;
                if (Math.abs(target - anim) < 0.005f) anim = target;
                openAnim.put(m, anim);

                if (anim > 0.01f) {
                    for (Setting<?> s : m.getSettings()) {
                        if (!supported(s) || !s.isVisible()) continue;
                        Row sr = new Row();
                        sr.type = Type.SETTING;
                        sr.panel = n;
                        sr.module = m;
                        sr.setting = s;
                        sr.x = px;
                        sr.y = cy;
                        sr.w = PANEL_W;
                        sr.h = (isSlider(s) ? SLIDER_H : SETTING_H) * anim;
                        sr.alpha = anim;
                        panelRows.add(sr);
                        cy += sr.h;
                    }
                }
            }

            float panelH = cy - py + PAD;

            // panel govdesi
            Render2DEngine.drawRound(ms, px, py, PANEL_W, panelH, 6f, new Color(14, 14, 18, 190));

            // satirlari ciz
            for (Row r : panelRows) {
                drawRow(ms, r, mouseX, mouseY, accent);
                rows.add(r);
            }
        }

        // slider surukleme
        if (draggingSlider != null && draggingSliderRow != null) {
            updateSlider(draggingSlider, draggingSliderRow, mouseX);
        }

        drawSearchBar(ms, mouseX, mouseY, accent);

        // aciklama
        if (!currentDescription.isEmpty()) {
            FontRenderer f = FontRenderers.sf_medium_mini;
            float tw = f.getStringWidth(currentDescription);
            float bx = width / 2f - tw / 2f - 6f;
            float by = height - 56f;
            Render2DEngine.drawRound(ms, bx, by, tw + 12f, 15f, 4f, new Color(14, 14, 18, 200));
            f.drawString(ms, currentDescription, bx + 6f, by + 4f, new Color(220, 220, 220).getRGB());
        }
    }

    // ===================================================================
    //  Cizim yardimcilari
    // ===================================================================
    private void drawRow(MatrixStack ms, Row r, int mx, int my, Color accent) {
        boolean hovered = inside(mx, my, r.x, r.y, r.w, r.h);

        switch (r.type) {
            case HEADER: {
                FontRenderer f = FontRenderers.sf_bold;
                String t = r.panel;
                f.drawString(ms, t, r.x + 8f, r.y + (r.h - f.getFontHeight(t)) / 2f + 1f, Color.WHITE.getRGB());
                // ince ayrac
                Render2DEngine.drawRect(ms, r.x + 6f, r.y + r.h - 1f, r.w - 12f, 0.6f, new Color(255, 255, 255, 28));
                break;
            }
            case MODULE: {
                Module m = r.module;
                float ea = enabledAnim.getOrDefault(m, m.isEnabled() ? 1f : 0f);
                ea += ((m.isEnabled() ? 1f : 0f) - ea) * 0.2f;
                enabledAnim.put(m, ea);

                if (hovered) {
                    anyHovered = true;
                    currentDescription = m.getDescription() == null ? "" : m.getDescription();
                    Render2DEngine.drawRound(ms, r.x + 3f, r.y + 1f, r.w - 6f, r.h - 2f, 4f, new Color(255, 255, 255, 14));
                }
                if (ea > 0.02f) {
                    Render2DEngine.drawRound(ms, r.x + 3f, r.y + 1f, r.w - 6f, r.h - 2f, 4f,
                            new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (70 * ea)));
                }

                FontRenderer f = FontRenderers.sf_medium;
                String name = m.getName();
                int shade = (int) (150 + 105 * ea);
                f.drawString(ms, name, r.x + 9f, r.y + (r.h - f.getFontHeight(name)) / 2f + 1f,
                        new Color(shade, shade, shade).getRGB());

                // "..." butonu
                float bx = r.x + r.w - 18f;
                boolean dotsHover = inside(mx, my, bx, r.y, 16f, r.h);
                int dotA = dotsHover || opened.getOrDefault(m, false) ? 255 : 130;
                float dy = r.y + r.h / 2f;
                for (int i = 0; i < 3; i++) {
                    Render2DEngine.drawRound(ms, bx + 3f + i * 4f, dy - 0.5f, 1.8f, 1.8f, 0.9f, new Color(255, 255, 255, dotA));
                }
                break;
            }
            case SETTING: {
                if (r.alpha < 0.55f) break; // cok kucukken yazi tasmasin
                int a = (int) (255 * Math.min(1f, (r.alpha - 0.55f) / 0.45f));
                drawSetting(ms, r, mx, my, accent, a);
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void drawSetting(MatrixStack ms, Row r, int mx, int my, Color accent, int a) {
        Setting<?> s = r.setting;
        FontRenderer f = FontRenderers.sf_medium_mini;
        float x = r.x + 9f;
        float w = r.w - 18f;
        boolean hovered = inside(mx, my, r.x, r.y, r.w, r.h);
        if (hovered) anyHovered = true;
        int white = new Color(220, 220, 220, a).getRGB();
        int grey = new Color(150, 150, 150, a).getRGB();
        String label = s.getName();

        Object v = s.getValue();

        if (v instanceof Boolean) {
            boolean on = (Boolean) v;
            f.drawString(ms, label, x, r.y + (SETTING_H - f.getFontHeight(label)) / 2f + 1f, on ? white : grey);
            float sw = 16f, sh = 8f;
            float sx = r.x + r.w - 9f - sw;
            float sy = r.y + (SETTING_H - sh) / 2f;
            Render2DEngine.drawRound(ms, sx, sy, sw, sh, 4f,
                    on ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), a) : new Color(60, 60, 66, a));
            float kx = on ? sx + sw - sh + 1f : sx + 1f;
            Render2DEngine.drawRound(ms, kx, sy + 1f, sh - 2f, sh - 2f, 3f, new Color(255, 255, 255, a));
        } else if (isSlider(s)) {
            double min = ((Number) s.getMin()).doubleValue();
            double max = ((Number) s.getMax()).doubleValue();
            double val = ((Number) v).doubleValue();
            float frac = (float) Math.max(0, Math.min(1, (val - min) / (max - min)));
            String vs = s.isInteger() ? String.valueOf((int) val) : String.format(java.util.Locale.US, "%.2f", val);

            f.drawString(ms, label, x, r.y + 2f, white);
            f.drawString(ms, vs, x + w - f.getStringWidth(vs), r.y + 2f, grey);

            float by = r.y + SLIDER_H - 6f;
            Render2DEngine.drawRound(ms, x, by, w, 3f, 1.5f, new Color(60, 60, 66, a));
            if (frac > 0.01f) {
                Render2DEngine.drawRound(ms, x, by, Math.max(3f, w * frac), 3f, 1.5f,
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), a));
            }
        } else if (s.isEnumSetting()) {
            f.drawString(ms, label, x, r.y + (SETTING_H - f.getFontHeight(label)) / 2f + 1f, white);
            String mode = s.currentEnumName();
            f.drawString(ms, mode, x + w - f.getStringWidth(mode), r.y + (SETTING_H - f.getFontHeight(mode)) / 2f + 1f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), a).getRGB());
        } else if (v instanceof Bind) {
            f.drawString(ms, label, x, r.y + (SETTING_H - f.getFontHeight(label)) / 2f + 1f, white);
            String b = bindListening == s ? "..." : ((Bind) v).getBind();
            if (b == null || b.isEmpty()) b = "NONE";
            f.drawString(ms, b, x + w - f.getStringWidth(b), r.y + (SETTING_H - f.getFontHeight(b)) / 2f + 1f, grey);
        }
    }

    private void drawSearchBar(MatrixStack ms, int mx, int my, Color accent) {
        searchX = width / 2f - searchW / 2f;
        searchY = height - 30f;
        boolean hovered = inside(mx, my, searchX, searchY, searchW, searchH);
        if (hovered) anyHovered = true;

        Render2DEngine.drawRound(ms, searchX, searchY, searchW, searchH, 6f,
                new Color(14, 14, 18, searchFocused ? 225 : 190));
        if (searchFocused) {
            Render2DEngine.drawRect(ms, searchX + 8f, searchY + searchH - 1.2f, searchW - 16f, 0.8f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 160));
        }

        FontRenderer f = FontRenderers.sf_medium;
        String shown = query.isEmpty() && !searchFocused ? "Search..." : query + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        int col = query.isEmpty() && !searchFocused ? new Color(130, 130, 135).getRGB() : Color.WHITE.getRGB();
        f.drawString(ms, shown, searchX + 9f, searchY + (searchH - f.getFontHeight("A")) / 2f + 1f, col);
    }

    private Color accent() {
        try {
            return ThunderHackGui.getColor(0);
        } catch (Throwable t) {
            return new Color(110, 140, 255);
        }
    }

    // ===================================================================
    //  Ayar yardimcilari
    // ===================================================================
    private boolean isSlider(Setting<?> s) {
        return s.isNumberSetting() && s.getMin() instanceof Number && s.getMax() instanceof Number && s.getValue() instanceof Number;
    }

    private boolean supported(Setting<?> s) {
        if ("Enabled".equalsIgnoreCase(s.getName())) return false;
        Object v = s.getValue();
        return v instanceof Boolean || isSlider(s) || s.isEnumSetting() || v instanceof Bind;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void updateSlider(Setting<?> s, Row r, double mouseX) {
        double min = ((Number) s.getMin()).doubleValue();
        double max = ((Number) s.getMax()).doubleValue();
        float x = r.x + 9f;
        float w = r.w - 18f;
        double frac = Math.max(0, Math.min(1, (mouseX - x) / w));
        double val = min + (max - min) * frac;
        Setting raw = s;
        if (s.isInteger()) raw.setValue(Integer.valueOf((int) Math.round(val)));
        else if (s.isFloat()) raw.setValue(Float.valueOf((float) (Math.round(val * 100.0) / 100.0)));
        else raw.setValue(Double.valueOf(Math.round(val * 100.0) / 100.0));
    }

    private static boolean inside(double mx, double my, double x, double y, double w, double h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // ===================================================================
    //  Girdi
    // ===================================================================
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // arama cubugu
        if (inside(mx, my, searchX, searchY, searchW, searchH)) {
            searchFocused = true;
            bindListening = null;
            return true;
        }
        searchFocused = false;

        // en ustteki (sonra cizilen) satirdan basla
        for (int i = rows.size() - 1; i >= 0; i--) {
            Row r = rows.get(i);
            if (!inside(mx, my, r.x, r.y, r.w, r.h)) continue;

            switch (r.type) {
                case HEADER:
                    if (button == 0) {
                        draggingPanel = r.panel;
                        float[] p = panelPos.get(r.panel);
                        dragOffX = (float) mx - p[0];
                        dragOffY = (float) my - (p[1] + scrollY);
                    }
                    return true;

                case MODULE: {
                    boolean dots = mx >= r.x + r.w - 18f;
                    if (button == 1 || (button == 0 && dots)) {
                        opened.put(r.module, !opened.getOrDefault(r.module, false));
                    } else if (button == 0) {
                        r.module.toggle();
                    }
                    return true;
                }

                case SETTING:
                    return clickSetting(r, mx, button);
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean clickSetting(Row r, double mx, int button) {
        Setting<?> s = r.setting;
        Object v = s.getValue();
        Setting raw = s;

        if (v instanceof Boolean) {
            if (button == 0) raw.setValue(!((Boolean) v));
            return true;
        }
        if (isSlider(s)) {
            if (button == 0) {
                draggingSlider = s;
                draggingSliderRow = r;
                updateSlider(s, r, mx);
            }
            return true;
        }
        if (s.isEnumSetting()) {
            if (button == 0) s.increaseEnum();
            return true;
        }
        if (v instanceof Bind) {
            if (button == 0) bindListening = (bindListening == s) ? null : s;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingPanel = null;
        draggingSlider = null;
        draggingSliderRow = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scrollY += (float) vertical * 18f;
        if (scrollY > 0f) scrollY = 0f;
        if (scrollY < -2000f) scrollY = -2000f;
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocused && chr >= 32 && chr != 127 && query.length() < 24) {
            query += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // bind dinleme
        if (bindListening != null) {
            Setting raw = bindListening;
            Object old = raw.getValue();
            boolean hold = old instanceof Bind && ((Bind) old).isHold();
            int key = (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) ? -1 : keyCode;
            Bind nb = new Bind(key, hold, false);
            if ("bind".equalsIgnoreCase(bindListening.getName()) && bindListening.getModule() != null) {
                bindListening.getModule().setBind(nb);
            } else {
                raw.setValue(nb);
            }
            bindListening = null;
            return true;
        }

        // arama
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
                return true;
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
