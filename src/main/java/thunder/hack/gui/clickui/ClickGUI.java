package thunder.hack.gui.clickui;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.ClickGui;
import thunder.hack.features.modules.client.ClientSettings;
import thunder.hack.features.modules.client.HudEditor;
import thunder.hack.gui.font.FontRenderers;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.utility.render.animation.EaseOutBack;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static thunder.hack.features.modules.Module.fullNullCheck;

/**
 * ThunderHack tarzı ClickGUI — kategoriler soldan sağa TEK SIRA.
 *
 * Neden böyle yazıldı (bytecode'dan çıkarılanlar):
 *  - Category.render() her karede width'i ClickGui.moduleWidth'e zorluyor,
 *    yani pencere genişliğini küçültüp sığdıramazsın -> tüm GUI'yi ölçekliyoruz.
 *  - AbstractCategory.restorePos() x,y'yi sx,sy'den (varsayılan 0,0) geri yazıyor,
 *    bu yüzden hiç kullanılmıyor; konumları kendimiz tutuyoruz.
 *  - Konumlar her karede zorlanır; sadece SOL TIK basılıyken (sürükleme) pencerenin
 *    kendi güncellediği konum kabul edilir. Böylece dışarıdan bir şey x,y'yi
 *    sıfırlasa bile pencereler yığılmaz.
 *
 * KULLANIM: ClickGui modülünün onEnable()'ında  mc.setScreen(ClickGUI.getClickGui());
 */
public class ClickGUI extends Screen {

    // ---- state ---------------------------------------------------------
    public static List<AbstractCategory> windows;
    public static boolean anyHovered;
    public static boolean close = false;
    public static String currentDescription = "";

    private static ClickGUI INSTANCE = new ClickGUI();

    /** ClickGui modülü onEnable'da buna erişiyor -> public olmalı. */
    public final EaseOutBack imageAnimation = new EaseOutBack(20);

    private boolean firstOpen;
    private float scrollY;
    private double closeAnimation;
    private int closeDirectionX, closeDirectionY;

    /** Düzen: GUI'yi ekrana sığdırmak için uygulanan ölçek (<= 1). */
    private float guiScale = 1f;
    private int lastScaledWidth = -1, lastScaledHeight = -1;

    /** Her pencerenin zorlanan konumu {x, y} (ölçeksiz "sanal" koordinat). */
    private final Map<AbstractCategory, float[]> targets = new IdentityHashMap<>();
    /** Kapanışta kaydedilen konumlar (kategori adı -> {x, y}). */
    private static final Map<String, float[]> SAVED = new HashMap<>();

    private long handCursor = 0L;
    private boolean handActive = false;

    private static final String[] TIPS_EN = {
            "Left Mouse Click to enable module",
            "Right Mouse Click to open module settings",
            "Middle Mouse Click to bind module",
            "Ctrl + F to start searching",
            "Drag n Drop config there to load",
            "Shift + Left Mouse Click to change module visibility in Array list",
            "Middle Mouse Click on slider to enter value from keyboard",
            "Delete + Left Mouse Click on module to reset"
    };
    private static final String[] TIPS_RU = {
            "ЛКМ — включить модуль",
            "ПКМ — открыть настройки модуля",
            "СКМ — назначить бинд",
            "Ctrl + F — поиск",
            "Перетащи конфиг сюда, чтобы загрузить",
            "Shift + ЛКМ — показать/скрыть модуль в Array list",
            "СКМ по слайдеру — ввести значение с клавиатуры",
            "Delete + ЛКМ по модулю — сбросить"
    };
    private int tipIndex = 0;
    private long lastTipSwitch = System.currentTimeMillis();

    // ---- ctor / instance ----------------------------------------------
    protected ClickGUI() {
        super(Text.of("NewClickGUI"));
        windows = Lists.newArrayList();
        firstOpen = true;
        setInstance();
    }

    public static ClickGUI getClickGui() {
        INSTANCE = INSTANCE == null ? new ClickGUI() : INSTANCE;
        return INSTANCE;
    }

    public static ClickGUI getInstance() {
        return getClickGui();
    }

    private void setInstance() {
        INSTANCE = this;
    }

    // ---- init ----------------------------------------------------------
    @Override
    protected void init() {
        close = false;
        closeAnimation = 0;
        scrollY = 0;
        imageAnimation.reset();

        int sw = mc().getWindow().getScaledWidth();
        int sh = mc().getWindow().getScaledHeight();
        boolean resized = sw != lastScaledWidth || sh != lastScaledHeight;

        if (firstOpen || windows.isEmpty()) {
            buildWindows();
            firstOpen = false;
            resized = true;
        }

        layoutWindows(sw, resized);

        lastScaledWidth = sw;
        lastScaledHeight = sh;

        windows.forEach(AbstractCategory::init);
    }

    private void buildWindows() {
        windows.clear();
        targets.clear();

        float moduleWidth = ModuleManager.clickGui.moduleWidth.getValue();
        float catHeight = ModuleManager.clickGui.catHeight.getValue();

        for (Module.Category c : Managers.MODULE.getCategories()) {
            if (c == Module.Category.HUD) continue;

            Category window = new Category(
                    c,
                    Managers.MODULE.getModulesByCategory(c),
                    0f, 0f,
                    moduleWidth,
                    catHeight
            );
            window.setOpen(true);
            windows.add(window);
        }
    }

    /**
     * Kategorileri soldan sağa TEK SIRA dizer, ortalar.
     * Toplam genişlik ekrana sığmazsa tüm GUI küçültülür (guiScale).
     */
    private void layoutWindows(int screenWidth, boolean forceDefault) {
        int n = windows.size();
        if (n == 0) return;

        final float gap = 4f;
        final float margin = 8f;
        final float top = 20f;

        // Category.render() width'i zaten moduleWidth'e zorluyor -> gerçek genişlik bu
        float width = ModuleManager.clickGui.moduleWidth.getValue();
        float totalWidth = n * width + (n - 1) * gap;

        guiScale = Math.min(1f, (screenWidth - margin * 2f) / totalWidth);
        guiScale = Math.max(guiScale, 0.4f);

        float virtualWidth = screenWidth / guiScale;
        float startX = Math.max(margin, (virtualWidth - totalWidth) / 2f);

        for (int i = 0; i < n; i++) {
            AbstractCategory w = windows.get(i);
            w.setWidth(width);

            float[] saved = SAVED.get(w.getName());
            float x, y;
            if (!forceDefault && saved != null) {
                x = saved[0];
                y = saved[1];
            } else {
                x = startX + i * (width + gap);
                y = top;
            }
            w.setX(x);
            w.setY(y);
            targets.put(w, new float[]{x, y});
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        windows.forEach(AbstractCategory::tick);
        imageAnimation.update(true);
        super.tick();
    }

    // ---- koordinat yardımcıları ---------------------------------------
    private int mx(double mouseX) {
        return (int) (mouseX / guiScale);
    }

    private int my(double mouseY) {
        return (int) (mouseY / guiScale - scrollY);
    }

    private boolean leftDown() {
        return GLFW.glfwGetMouseButton(mc().getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    // ---- render --------------------------------------------------------
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (fullNullCheck()) return;

        anyHovered = false;
        currentDescription = "";

        ClickGui cfg = ModuleManager.clickGui;

        if (cfg.blur.getValue()) applyBlur(delta);

        renderImage(context);

        // kapanış animasyonu
        float closeScale = 1f;
        if (close) {
            if (cfg.closeAnimation.getValue()) {
                closeAnimation = Math.min(1.0, closeAnimation + 0.08 * (60.0 / Math.max(1, mc().getCurrentFps())));
                closeScale = (float) (1.0 - closeAnimation);
                if (closeAnimation >= 1.0) {
                    super.close();
                    return;
                }
            } else {
                super.close();
                return;
            }
        }

        MatrixStack matrices = context.getMatrices();
        matrices.push();

        float cx = mc().getWindow().getScaledWidth() / 2f;
        float cy = mc().getWindow().getScaledHeight() / 2f;

        if (close && cfg.closeAnimation.getValue()) {
            matrices.translate(cx + closeDirectionX * (float) closeAnimation * 40f,
                    cy + closeDirectionY * (float) closeAnimation * 40f, 0);
            matrices.scale(closeScale, closeScale, 1f);
            matrices.translate(-cx, -cy, 0);
        }

        // tüm GUI ekrana sığsın diye ölçek, sonra scroll
        matrices.scale(guiScale, guiScale, 1f);
        matrices.translate(0, scrollY, 0);

        int fmx = mx(mouseX);
        int fmy = my(mouseY);
        boolean dragPhase = leftDown();

        for (AbstractCategory w : windows) {
            float[] t = targets.computeIfAbsent(w, k -> new float[]{w.getX(), w.getY()});

            // sürükleme yokken konumu ZORLA (yığılmayı engeller)
            if (!dragPhase) {
                w.setX(t[0]);
                w.setY(t[1]);
            }

            w.render(context, fmx, fmy, delta);

            // sürüklerken pencerenin güncellediği konumu kabul et
            if (dragPhase) {
                t[0] = w.getX();
                t[1] = w.getY();
            }
        }

        matrices.pop();

        renderFooter(context);
        updateCursor();

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderImage(DrawContext context) {
        ClickGui cfg = ModuleManager.clickGui;
        if (cfg.image.getValue() == ClickGui.Image.None) return;

        double anim = imageAnimation.getAnimationd();
        RenderSystem.setShaderTexture(0, cfg.image.getValue().file);

        double w = cfg.image.getValue().fileWidth;
        double h = cfg.image.getValue().fileHeight;
        double x = mc().getWindow().getScaledWidth() - w * anim;
        double y = mc().getWindow().getScaledHeight() - h;

        Render2DEngine.renderTexture(context.getMatrices(), x, y, w, h, 0, 0, w, h, w, h);
    }

    private void renderFooter(DrawContext context) {
        ClickGui cfg = ModuleManager.clickGui;
        MatrixStack ms = context.getMatrices();

        float centerX = mc().getWindow().getScaledWidth() / 2f;
        float bottomY = mc().getWindow().getScaledHeight() - 24f;

        if (cfg.descriptions.getValue() && !currentDescription.isEmpty()) {
            float tw = FontRenderers.sf_medium.getStringWidth(currentDescription);
            Render2DEngine.drawHudBase(ms, centerX - tw / 2f - 6f, bottomY - 20f, tw + 12f, 14f, 3f, false);
            FontRenderers.sf_medium.drawString(ms, currentDescription,
                    centerX - tw / 2f, bottomY - 16f, HudEditor.getColor(1).getRGB());
        }

        if (cfg.tips.getValue()) {
            long now = System.currentTimeMillis();
            if (now - lastTipSwitch > 5000L) {
                tipIndex = (tipIndex + 1) % TIPS_EN.length;
                lastTipSwitch = now;
            }
            String tip = ClientSettings.isRu() ? TIPS_RU[tipIndex] : TIPS_EN[tipIndex];
            float tw = FontRenderers.sf_medium.getStringWidth(tip);
            Render2DEngine.drawHudBase(ms, centerX - tw / 2f - 6f, bottomY, tw + 12f, 14f, 3f, false);
            FontRenderers.sf_medium.drawString(ms, tip,
                    centerX - tw / 2f, bottomY + 4f, 0xFFAAAAAA);
        }
    }

    private void updateCursor() {
        long handle = mc().getWindow().getHandle();
        try {
            if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) return;

            if (anyHovered && !handActive) {
                if (handCursor == 0L) handCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_POINTING_HAND_CURSOR);
                GLFW.glfwSetCursor(handle, handCursor);
                handActive = true;
            } else if (!anyHovered && handActive) {
                GLFW.glfwSetCursor(handle, 0L);
                handActive = false;
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // blur / karartmayı render() içinde kendimiz yapıyoruz
    }

    // ---- input ---------------------------------------------------------
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (ModuleManager.clickGui.scrollMode.getValue() == ClickGui.scrollModeEn.Old) {
            // eski mod: sadece imlecin altındaki kategori kayar. setModuleOffset(offset, mouseX, mouseY)
            final int fx = mx(mouseX), fy = my(mouseY);
            windows.forEach(w -> w.setModuleOffset((float) verticalAmount * 5f, fx, fy));
        } else {
            // yeni mod: tüm GUI kayar
            scrollY += (float) verticalAmount * 10f;
            scrollY = Math.max(-400f, Math.min(0f, scrollY));
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        final int fx = mx(mouseX), fy = my(mouseY);

        // tıklanan pencereyi öne al
        AbstractCategory clicked = null;
        for (int i = windows.size() - 1; i >= 0; i--) {
            AbstractCategory w = windows.get(i);
            if (Render2DEngine.isHovered(fx, fy, w.getX(), w.getY(), w.getWidth(), w.getHeight())) {
                clicked = w;
                break;
            }
        }
        if (clicked != null && windows.get(windows.size() - 1) != clicked) {
            windows.remove(clicked);
            windows.add(clicked);
        }

        windows.forEach(w -> w.mouseClicked(fx, fy, button));
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        final int fx = mx(mouseX), fy = my(mouseY);
        windows.forEach(w -> w.mouseReleased(fx, fy, button));
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        windows.forEach(w -> w.charTyped(chr, modifiers));
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean consumed = false;
        for (AbstractCategory w : windows)
            consumed |= w.keyTyped(keyCode);

        if (!consumed && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return consumed || super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- close ---------------------------------------------------------
    @Override
    public void close() {
        if (close) return;

        for (AbstractCategory w : windows) {
            float[] t = targets.get(w);
            SAVED.put(w.getName(), t != null ? new float[]{t[0], t[1]} : new float[]{w.getX(), w.getY()});
        }
        windows.forEach(AbstractCategory::onClose);

        if (handActive) {
            GLFW.glfwSetCursor(mc().getWindow().getHandle(), 0L);
            handActive = false;
        }

        close = true;
        if (ModuleManager.clickGui.closeAnimation.getValue()) {
            closeAnimation = 0;
            closeDirectionX = Math.random() < 0.5 ? -1 : 1;
            closeDirectionY = Math.random() < 0.5 ? -1 : 1;
        } else {
            super.close();
        }
    }

    // ---- helpers -------------------------------------------------------
    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }
}
