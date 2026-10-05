package thunder.hack.gui.clickui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import thunder.hack.core.Managers;
import thunder.hack.features.modules.Module;
import thunder.hack.gui.font.FontRenderers;
import thunder.hack.setting.Setting;
import thunder.hack.utility.render.Render2DEngine;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FelixDlc ClickGUI (ThunderHack stili)
 * - Her kategori ayri, SURUKLENEBILIR panel (basliga basili tut + surukle)
 * - Sol tik: modul ac/kapat | Sag tik: ayarlari ac | Scroll: panel icinde kaydir
 * - Altta arama cubugu, modul uzerine gelince aciklama yazisi
 * - Font: ThunderHack'in kendi fontu (FontRenderers.sf_medium)
 *
 * Derleme hatasi cikarsa SADECE "API KOPRUSU" bolumunu duzelt.
 */
public class ClickGUI extends Screen {

    // ===================== API KOPRUSU =====================
    private static List<Module> allModules() {
        return Managers.MODULE.modules;
    }

    private static String catKey(Module m) {
        return m.getCategory().getName();
    }

    private static void drawRound(DrawContext c, float x, float y, float w, float h, float r, Color col) {
        Render2DEngine.drawRound(c.getMatrices(), x, y, w, h, r, col);
    }

    private static List<Setting<?>> settingsOf(Module m) {
        return new ArrayList<>(m.getSettings());
    }

    // Font koprusu. Font hata verirse bu iki metodu asagidaki vanilla satirlarla degistir:
    //   text:  c.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, s, (int) x, (int) y, col);
    //   textW: return MinecraftClient.getInstance().textRenderer.getWidth(s);
    private static void text(DrawContext c, String s, float x, float y, int col) {
        FontRenderers.sf_medium.drawString(c.getMatrices(), s, x, y, col);
    }

    private static float textW(String s) {
        return FontRenderers.sf_medium.getStringWidth(s);
    }
    // =======================================================

    private static ClickGUI instance;

    // Diger dosyalarin kullandigi alanlar
    public static boolean anyHovered = false;
    public static boolean close = false;
    public static String currentDescription = "";

    public final ImageAnim imageAnimation = new ImageAnim();

    public static class ImageAnim {
        public void reset() {
        }
    }

    public static ClickGUI getClickGui() {
        if (instance == null) instance = new ClickGUI();
        return instance;
    }

    public static void open() {
        MinecraftClient.getInstance().setScreen(getClickGui());
    }

    private static final int PANEL_W = 112, HEADER_H = 20, ROW_H = 17, GAP = 8, MAX_BODY = 280, SET_H = 16;
    private static final Color ACCENT = new Color(140, 90, 240);
    private static final Color PANEL_BG = new Color(18, 17, 28, 235);
    private static final Color ROW_BG = new Color(34, 32, 50, 210);

    private final Map<String, float[]> pos = new LinkedHashMap<>();
    private final Map<String, Float> scroll = new HashMap<>();
    private final Map<Module, Boolean> expanded = new HashMap<>();
    private final Map<Module, Float> anim = new HashMap<>();

    private String dragCat;
    private float dragDX, dragDY;
    private Setting<?> slider;
    private float sliderX, sliderW;
    private String search = "";
    private boolean searching;
    private float openAnim;

    public ClickGUI() {
        super(Text.literal("FelixGui"));
    }

    @Override
    protected void init() {
        openAnim = 0f;
        // kategorileri modullerden bul (HUD elemanlari haric)
        List<String> cats = new ArrayList<>();
        for (Module m : allModules()) {
            String c = catKey(m);
            if (c.equalsIgnoreCase("HUD")) continue;
            if (!cats.contains(c)) cats.add(c);
        }
        float total = cats.size() * PANEL_W + (cats.size() - 1) * GAP;
        float x = (width - total) / 2f;
        for (String c : cats) {
            if (!pos.containsKey(c)) pos.put(c, new float[]{x, 30});
            x += PANEL_W + GAP;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------ helpers
    private List<Module> modulesOf(String cat) {
        List<Module> out = new ArrayList<>();
        for (Module m : allModules()) {
            if (!catKey(m).equals(cat)) continue;
            if (!search.isEmpty() && !m.getName().toLowerCase().contains(search.toLowerCase())) continue;
            out.add(m);
        }
        return out;
    }

    private float rowHeight(Module m) {
        float h = ROW_H;
        if (expanded.getOrDefault(m, false)) {
            for (Setting<?> s : settingsOf(m)) if (s.isVisible()) h += SET_H + 2;
            h += 2;
        }
        return h;
    }

    private float contentHeight(List<Module> mods) {
        float h = 3;
        for (Module m : mods) h += rowHeight(m) + 2;
        return h;
    }

    private float bodyHeight(List<Module> mods) {
        return Math.min(MAX_BODY, contentHeight(mods));
    }

    // ------------------------------------------------------------ render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        openAnim = Math.min(1f, openAnim + delta * 0.12f);
        ctx.fill(0, 0, width, height, new Color(0, 0, 0, (int) (100 * openAnim)).getRGB());

        anyHovered = false;
        currentDescription = "";

        for (Map.Entry<String, float[]> e : pos.entrySet()) {
            renderPanel(ctx, e.getKey(), e.getValue()[0], e.getValue()[1], mx, my, delta);
        }

        // arama cubugu
        float sw = 200, sh = 20, sx = (width - sw) / 2f, sy = height - 40;
        drawRound(ctx, sx, sy, sw, sh, 6, new Color(18, 17, 28, 235));
        String t = search.isEmpty() && !searching ? "Search..." : search + (searching && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        text(ctx, t, sx + 8, sy + 6, search.isEmpty() ? 0xFF8888A0 : 0xFFFFFFFF);

        // aciklama
        if (!currentDescription.isEmpty()) {
            float dw = textW(currentDescription) + 16;
            drawRound(ctx, (width - dw) / 2f, sy - 24, dw, 18, 5, new Color(18, 17, 28, 235));
            text(ctx, currentDescription, (width - dw) / 2f + 8, sy - 19, 0xFFD8D8E4);
        }
        super.render(ctx, mx, my, delta);
    }

    private void renderPanel(DrawContext ctx, String cat, float px, float py, int mx, int my, float delta) {
        List<Module> mods = modulesOf(cat);
        float bh = bodyHeight(mods);

        drawRound(ctx, px, py, PANEL_W, HEADER_H + bh + 4, 8, PANEL_BG);
        drawRound(ctx, px, py, PANEL_W, HEADER_H, 8, new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 200));
        text(ctx, cat, px + (PANEL_W - textW(cat)) / 2f, py + 6, 0xFFFFFFFF);

        float maxScroll = Math.max(0, contentHeight(mods) - bh);
        float sc = Math.max(-maxScroll, Math.min(0, scroll.getOrDefault(cat, 0f)));
        scroll.put(cat, sc);

        ctx.enableScissor((int) px, (int) (py + HEADER_H), (int) (px + PANEL_W), (int) (py + HEADER_H + bh + 2));
        float y = py + HEADER_H + 3 + sc;
        for (Module m : mods) {
            float rh = rowHeight(m);
            if (y + rh > py + HEADER_H && y < py + HEADER_H + bh + 2)
                renderModule(ctx, m, px + 4, y, PANEL_W - 8, mx, my, delta, py + HEADER_H, py + HEADER_H + bh);
            y += rh + 2;
        }
        ctx.disableScissor();
    }

    private void renderModule(DrawContext ctx, Module m, float x, float y, float w, int mx, int my, float delta, float clipTop, float clipBot) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + ROW_H && my >= clipTop && my <= clipBot;
        float a = anim.getOrDefault(m, 0f);
        a += ((m.isEnabled() ? 1f : 0f) - a) * Math.min(1f, delta * 0.35f);
        anim.put(m, a);

        if (hov) {
            anyHovered = true;
            currentDescription = m.getDescription() == null ? "" : m.getDescription();
        }

        drawRound(ctx, x, y, w, ROW_H, 5, new Color(ROW_BG.getRed() + (hov ? 8 : 0), ROW_BG.getGreen() + (hov ? 8 : 0), ROW_BG.getBlue() + (hov ? 10 : 0), ROW_BG.getAlpha()));
        if (a > 0.02f)
            drawRound(ctx, x, y, w, ROW_H, 5, new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), (int) (170 * a)));

        text(ctx, m.getName(), x + 6, y + 5, m.isEnabled() ? 0xFFFFFFFF : 0xFFC8C8D6);
        if (!settingsOf(m).isEmpty()) {
            String ar = expanded.getOrDefault(m, false) ? "-" : "+";
            text(ctx, ar, x + w - 10, y + 5, 0xFFB8B8C8);
        }

        if (expanded.getOrDefault(m, false)) {
            float sy = y + ROW_H + 2;
            for (Setting<?> s : settingsOf(m)) {
                if (!s.isVisible()) continue;
                renderSetting(ctx, s, x + 3, sy, w - 6);
                sy += SET_H + 2;
            }
        }
    }

    private void renderSetting(DrawContext ctx, Setting<?> s, float x, float y, float w) {
        Object v = s.getValue();
        drawRound(ctx, x, y, w, SET_H, 4, new Color(26, 25, 40, 220));
        if (v instanceof Boolean b) {
            text(ctx, s.getName(), x + 5, y + 4, 0xFFD8D8E4);
            drawRound(ctx, x + w - 22, y + 4, 17, 8, 4, b ? ACCENT : new Color(70, 68, 90));
            drawRound(ctx, x + w - (b ? 13 : 21), y + 5, 6, 6, 3, Color.WHITE);
        } else if (v instanceof Number n) {
            double min = ((Number) s.getMin()).doubleValue(), max = ((Number) s.getMax()).doubleValue();
            double pct = Math.max(0, Math.min(1, (n.doubleValue() - min) / Math.max(0.0001, max - min)));
            drawRound(ctx, x + 3, y + SET_H - 4, (float) ((w - 6) * pct), 2, 1, ACCENT);
            text(ctx, s.getName(), x + 5, y + 2, 0xFFD8D8E4);
            String t = (v instanceof Integer) ? String.valueOf(n.intValue()) : String.format("%.1f", n.doubleValue());
            text(ctx, t, x + w - 5 - textW(t), y + 2, 0xFFFFFFFF);
        } else if (v instanceof Enum<?> en) {
            text(ctx, s.getName(), x + 5, y + 4, 0xFFD8D8E4);
            String t = en.name();
            text(ctx, t, x + w - 5 - textW(t), y + 4, 0xFFB794FF);
        } else {
            text(ctx, s.getName(), x + 5, y + 4, 0xFF8888A0);
        }
    }

    // ------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        float sw = 200, sh = 20, sx = (width - sw) / 2f, sy = height - 40;
        searching = mx >= sx && mx <= sx + sw && my >= sy && my <= sy + sh;
        if (searching) return true;

        // ustteki panel once tiklansin diye tersten tara
        List<String> keys = new ArrayList<>(pos.keySet());
        for (int i = keys.size() - 1; i >= 0; i--) {
            String cat = keys.get(i);
            float px = pos.get(cat)[0], py = pos.get(cat)[1];
            List<Module> mods = modulesOf(cat);
            float bh = bodyHeight(mods);
            if (mx < px || mx > px + PANEL_W || my < py || my > py + HEADER_H + bh + 4) continue;

            if (my <= py + HEADER_H) {
                if (button == 0) {
                    dragCat = cat;
                    dragDX = (float) mx - px;
                    dragDY = (float) my - py;
                }
                return true;
            }
            if (my > py + HEADER_H + bh + 2) return true;

            float y = py + HEADER_H + 3 + scroll.getOrDefault(cat, 0f);
            for (Module m : mods) {
                float x = px + 4, w = PANEL_W - 8;
                if (my >= y && my <= y + ROW_H && mx >= x && mx <= x + w) {
                    if (button == 0) m.toggle();
                    else if (button == 1) expanded.put(m, !expanded.getOrDefault(m, false));
                    return true;
                }
                if (expanded.getOrDefault(m, false)) {
                    float st = y + ROW_H + 2;
                    for (Setting<?> s : settingsOf(m)) {
                        if (!s.isVisible()) continue;
                        if (my >= st && my <= st + SET_H && mx >= x + 3 && mx <= x + w - 3) {
                            clickSetting(s, x + 3, w - 6, mx, button);
                            return true;
                        }
                        st += SET_H + 2;
                    }
                }
                y += rowHeight(m) + 2;
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void clickSetting(Setting s, float x, float w, double mx, int button) {
        Object v = s.getValue();
        if (v instanceof Boolean b) {
            s.setValue(!b);
        } else if (v instanceof Number) {
            slider = s;
            sliderX = x + 3;
            sliderW = w - 6;
            applySlider(mx);
        } else if (v instanceof Enum<?> en) {
            Object[] all = en.getDeclaringClass().getEnumConstants();
            int idx = en.ordinal() + (button == 1 ? -1 : 1);
            s.setValue(all[(idx + all.length) % all.length]);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applySlider(double mx) {
        if (slider == null) return;
        Setting s = slider;
        double min = ((Number) s.getMin()).doubleValue(), max = ((Number) s.getMax()).doubleValue();
        double pct = Math.max(0, Math.min(1, (mx - sliderX) / sliderW));
        double val = min + (max - min) * pct;
        Object cur = s.getValue();
        if (cur instanceof Integer) s.setValue((int) Math.round(val));
        else if (cur instanceof Float) s.setValue((float) (Math.round(val * 10) / 10.0));
        else if (cur instanceof Double) s.setValue(Math.round(val * 10) / 10.0);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragCat != null) {
            float[] p = pos.get(dragCat);
            p[0] = (float) mx - dragDX;
            p[1] = (float) my - dragDY;
            return true;
        }
        if (slider != null) {
            applySlider(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragCat = null;
        slider = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        for (Map.Entry<String, float[]> e : pos.entrySet()) {
            float px = e.getValue()[0], py = e.getValue()[1];
            float bh = bodyHeight(modulesOf(e.getKey()));
            if (mx >= px && mx <= px + PANEL_W && my >= py && my <= py + HEADER_H + bh + 4) {
                scroll.put(e.getKey(), scroll.getOrDefault(e.getKey(), 0f) + (float) v * 18f);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searching) {
            search += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (searching) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER) {
                searching = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searching = false;
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // kendi karartmamizi render() icinde ciziyoruz
    }
}
