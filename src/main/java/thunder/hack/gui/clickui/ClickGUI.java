package thunder.hack.gui.clickui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import thunder.hack.core.Managers;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.render.Render2DEngine;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FelixDlc ClickGUI - 5 koyu yuvarlak panel (Combat / Movement / Visuals / Player / Miscellaneous)
 * Sol tik: modulu ac/kapat | Sag tik veya "...": ayarlari ac | Scroll: kaydir | Altta arama
 *
 * Derleme hatasi cikarsa SADECE asagidaki "API KOPRUSU" bolumundeki 4 metodu duzelt.
 */
public class ClickGUI extends Screen {

    // ===================== API KOPRUSU (forkundaki isimlere gore duzelt) =====================
    private static List<Module> allModules() {
        return Managers.MODULE.modules;                 // eski surumlerde: ThunderHack.moduleManager.modules
    }

        private static String catKey(Module m) {
        String n = m.getCategory().getName().toUpperCase();
        if (n.equals("VISUALS")) return "RENDER";
        if (n.equals("MISCELLANEOUS")) return "MISC";
        return n;
    }                 // COMBAT, MOVEMENT, RENDER, PLAYER, MISC ...
    }

    private static void drawRound(DrawContext c, float x, float y, float w, float h, float r, Color col) {
        Render2DEngine.drawRound(c.getMatrices(), x, y, w, h, r, col);
    }

    private static List<Setting<?>> settingsOf(Module m) {
        return new ArrayList<>(m.getSettings());        // bazi surumlerde List<Setting<?>> zaten
    }
    // ========================================================================================

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

    private static final Map<String, String> PANELS = new LinkedHashMap<>();
    static {
        PANELS.put("COMBAT", "Combat");
        PANELS.put("MOVEMENT", "Movement");
        PANELS.put("RENDER", "Visuals");
        PANELS.put("PLAYER", "Player");
        PANELS.put("MISC", "Miscellaneous");
    }

    private static final int PANEL_W = 224, PANEL_H = 255, HEADER_H = 30, ROW_H = 24, PAD = 8, GAP = 12;

    private final Map<String, Float> scroll = new LinkedHashMap<>();
    private final Map<Module, Boolean> expanded = new java.util.HashMap<>();
    private final Map<Module, Float> hoverAnim = new java.util.HashMap<>();
    private Setting<?> draggingSlider;
    private float sliderX, sliderW;
    private String search = "";
    private boolean searching;
    private float openAnim;

    public ClickGUI() {
        super(Text.literal("FelixGui"));
    }

    public static void open() {
        MinecraftClient.getInstance().setScreen(getClickGui());
    }

    @Override
    protected void init() {
        openAnim = 0f;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ----------------------------------------------------------------- layout
    private float startX() {
        float total = PANELS.size() * PANEL_W + (PANELS.size() - 1) * GAP;
        return (width - total) / 2f;
    }

    private float startY() {
        return Math.max(20, (height - PANEL_H) / 2f - 14);
    }

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
        if (!expanded.getOrDefault(m, false)) return ROW_H;
        float h = ROW_H;
        for (Setting<?> s : settingsOf(m)) if (s.isVisible()) h += 20;
        return h + 4;
    }

    private float contentHeight(List<Module> mods) {
        float h = 0;
        for (Module m : mods) h += rowHeight(m) + 3;
        return h;
    }

    // ----------------------------------------------------------------- render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        openAnim = Math.min(1f, openAnim + delta * 0.12f);
        int dim = (int) (110 * openAnim);
        ctx.fill(0, 0, width, height, new Color(0, 0, 0, dim).getRGB());

        float x0 = startX(), y0 = startY() + (1f - openAnim) * 14f;
        int i = 0;
        for (Map.Entry<String, String> e : PANELS.entrySet()) {
            float px = x0 + i * (PANEL_W + GAP);
            renderPanel(ctx, e.getKey(), e.getValue(), px, y0, mx, my, delta);
            i++;
        }
        renderSearch(ctx, y0);
        super.render(ctx, mx, my, delta);
    }

    private void renderPanel(DrawContext ctx, String key, String title, float px, float py, int mx, int my, float delta) {
        int a = (int) (235 * openAnim);
        drawRound(ctx, px, py, PANEL_W, PANEL_H, 12, new Color(22, 20, 36, a));
        ctx.drawCenteredTextWithShadow(textRenderer, title, (int) (px + PANEL_W / 2f), (int) (py + 11), 0xFFFFFFFF);

        List<Module> mods = modulesOf(key);
        float viewH = PANEL_H - HEADER_H - 8;
        float maxScroll = Math.max(0, contentHeight(mods) - viewH);
        float sc = Math.max(-maxScroll, Math.min(0, scroll.getOrDefault(key, 0f)));
        scroll.put(key, sc);

        ctx.enableScissor((int) px, (int) (py + HEADER_H), (int) (px + PANEL_W), (int) (py + PANEL_H - 6));
        float y = py + HEADER_H + sc;
        for (Module m : mods) {
            float rh = rowHeight(m);
            if (y + rh > py + HEADER_H - 2 && y < py + PANEL_H) renderModule(ctx, m, px + PAD, y, PANEL_W - PAD * 2, mx, my, delta);
            y += rh + 3;
        }
        ctx.disableScissor();

        if (maxScroll > 0) {
            float bh = Math.max(20, viewH * (viewH / (viewH + maxScroll)));
            float by = py + HEADER_H + (-sc / maxScroll) * (viewH - bh);
            drawRound(ctx, px + PANEL_W - 5, by, 2, bh, 1, new Color(255, 255, 255, 60));
        }
    }

    private void renderModule(DrawContext ctx, Module m, float x, float y, float w, int mx, int my, float delta) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + ROW_H && inClip(mx, my);
        float ha = hoverAnim.getOrDefault(m, 0f);
        ha += ((hov ? 1f : 0f) - ha) * Math.min(1f, delta * 0.4f);
        hoverAnim.put(m, ha);

        boolean on = m.isEnabled();
        Color bg = on ? new Color(120, 70, 220, 150) : new Color(40, 38, 58, (int) (150 + 40 * ha));
        drawRound(ctx, x, y, w, ROW_H, 6, bg);

        ctx.drawTextWithShadow(textRenderer, m.getName(), (int) x + 8, (int) y + 8, on ? 0xFFFFFFFF : 0xFFD8D8E4);
        ctx.drawTextWithShadow(textRenderer, "...", (int) (x + w - 16), (int) y + 7, 0xFFFFFFFF);

        if (expanded.getOrDefault(m, false)) {
            float sy = y + ROW_H + 2;
            for (Setting<?> s : settingsOf(m)) {
                if (!s.isVisible()) continue;
                renderSetting(ctx, s, x + 4, sy, w - 8);
                sy += 20;
            }
        }
    }

    private void renderSetting(DrawContext ctx, Setting<?> s, float x, float y, float w) {
        Object v = s.getValue();
        drawRound(ctx, x, y, w, 18, 5, new Color(30, 28, 46, 190));
        if (v instanceof Boolean b) {
            ctx.drawTextWithShadow(textRenderer, s.getName(), (int) x + 6, (int) y + 5, 0xFFD8D8E4);
            drawRound(ctx, x + w - 26, y + 4, 20, 10, 5, b ? new Color(140, 90, 240) : new Color(70, 68, 90));
            drawRound(ctx, x + w - (b ? 14 : 24), y + 5, 8, 8, 4, Color.WHITE);
        } else if (v instanceof Number n) {
            double min = ((Number) s.getMin()).doubleValue(), max = ((Number) s.getMax()).doubleValue();
            double pct = (n.doubleValue() - min) / Math.max(0.0001, max - min);
            drawRound(ctx, x + 4, y + 13, (float) ((w - 8) * pct), 3, 1.5f, new Color(150, 100, 255));
            ctx.drawTextWithShadow(textRenderer, s.getName(), (int) x + 6, (int) y + 2, 0xFFD8D8E4);
            String t = (v instanceof Integer) ? String.valueOf(n.intValue()) : String.format("%.1f", n.doubleValue());
            ctx.drawTextWithShadow(textRenderer, t, (int) (x + w - 6 - textRenderer.getWidth(t)), (int) y + 2, 0xFFFFFFFF);
        } else if (v instanceof Enum<?> en) {
            ctx.drawTextWithShadow(textRenderer, s.getName(), (int) x + 6, (int) y + 5, 0xFFD8D8E4);
            String t = en.name();
            ctx.drawTextWithShadow(textRenderer, t, (int) (x + w - 6 - textRenderer.getWidth(t)), (int) y + 5, 0xFFB794FF);
        } else {
            ctx.drawTextWithShadow(textRenderer, s.getName(), (int) x + 6, (int) y + 5, 0xFF8888A0);
        }
    }

    private void renderSearch(DrawContext ctx, float y0) {
        float w = 220, h = 22;
        float x = (width - w) / 2f, y = y0 + PANEL_H + 14;
        drawRound(ctx, x, y, w, h, 8, new Color(22, 20, 36, (int) (235 * openAnim)));
        String t = search.isEmpty() && !searching ? "Search..." : search + (searching && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        ctx.drawCenteredTextWithShadow(textRenderer, t, (int) (x + w / 2), (int) y + 7, search.isEmpty() ? 0xFF8888A0 : 0xFFFFFFFF);
    }

    // ----------------------------------------------------------------- input
    private boolean inClip(int mx, int my) {
        float x0 = startX(), y0 = startY();
        return my >= y0 + HEADER_H && my <= y0 + PANEL_H - 6 && mx >= x0 && mx <= x0 + PANELS.size() * (PANEL_W + GAP);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        float x0 = startX(), y0 = startY();

        float sw = 220, sx = (width - sw) / 2f, sy = y0 + PANEL_H + 14;
        searching = mx >= sx && mx <= sx + sw && my >= sy && my <= sy + 22;
        if (searching) return true;

        int i = 0;
        for (String key : PANELS.keySet()) {
            float px = x0 + i * (PANEL_W + GAP);
            i++;
            if (mx < px || mx > px + PANEL_W || my < y0 + HEADER_H || my > y0 + PANEL_H - 6) continue;

            float y = y0 + HEADER_H + scroll.getOrDefault(key, 0f);
            for (Module m : modulesOf(key)) {
                float x = px + PAD, w = PANEL_W - PAD * 2;
                if (my >= y && my <= y + ROW_H && mx >= x && mx <= x + w) {
                    boolean dots = mx >= x + w - 24;
                    if (button == 1 || (button == 0 && dots)) expanded.put(m, !expanded.getOrDefault(m, false));
                    else if (button == 0) m.toggle();
                    return true;
                }
                if (expanded.getOrDefault(m, false)) {
                    float st = y + ROW_H + 2;
                    for (Setting<?> s : settingsOf(m)) {
                        if (!s.isVisible()) continue;
                        if (my >= st && my <= st + 18 && mx >= x + 4 && mx <= x + w - 4) {
                            clickSetting(s, x + 4, w - 8, mx, button);
                            return true;
                        }
                        st += 20;
                    }
                }
                y += rowHeight(m) + 3;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void clickSetting(Setting s, float x, float w, double mx, int button) {
        Object v = s.getValue();
        if (v instanceof Boolean b) {
            s.setValue(!b);
        } else if (v instanceof Number) {
            draggingSlider = s;
            sliderX = x + 4;
            sliderW = w - 8;
            applySlider(mx);
        } else if (v instanceof Enum<?> en) {
            Object[] all = en.getDeclaringClass().getEnumConstants();
            int idx = en.ordinal() + (button == 1 ? -1 : 1);
            s.setValue(all[(idx + all.length) % all.length]);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applySlider(double mx) {
        if (draggingSlider == null) return;
        Setting s = draggingSlider;
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
        if (draggingSlider != null) {
            applySlider(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingSlider = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        float x0 = startX();
        int i = 0;
        for (String key : PANELS.keySet()) {
            float px = x0 + i * (PANEL_W + GAP);
            i++;
            if (mx >= px && mx <= px + PANEL_W) {
                scroll.put(key, scroll.getOrDefault(key, 0f) + (float) v * 22f);
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
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // bulanik arka plan yok; render() icinde kendi karartmamizi ciziyoruz
    }
}
