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

import java.util.List;

import static thunder.hack.features.modules.Module.fullNullCheck;

/**
 * ThunderHack-Recode tarzı ClickGUI (Fabric 1.21, Yarn mappings).
 *
 * Bu sınıf jar içindeki thunder.hack.gui.clickui API'sine göre yazıldı:
 *   AbstractCategory / Category / ModuleButton / AbstractElement
 *   ClickGui modülü ayarları: moduleWidth, catHeight, blur, image, scrollMode,
 *   descriptions, tips, closeAnimation, imageAnimation
 *
 * KULLANIM:
 *   ClickGui modülünün onEnable()'ında:  mc.setScreen(ClickGUI.getClickGui());
 *
 * NOT: Derleyip test edemedim (decompiler/kaynak yoktu). Fork'ta imza uyuşmazlığı
 * çıkarsa, "// CHECK" yazan satırlara bak.
 */
public class ClickGUI extends Screen {

    // ---- state ---------------------------------------------------------
    public static List<AbstractCategory> windows;
    public static boolean anyHovered;
    public static boolean close = false;
    public static String currentDescription = "";

    private static ClickGUI INSTANCE = new ClickGUI();

    private boolean firstOpen;
    private float scrollY;
    private double closeAnimation;
    private float prevYaw, prevPitch;
    private int closeDirectionX, closeDirectionY;
    private int imageDirection;
    private final EaseOutBack imageAnimation = new EaseOutBack(20);

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

        if (firstOpen) {
            windows.clear();

            float moduleWidth = ModuleManager.clickGui.moduleWidth.getValue();
            float catHeight = ModuleManager.clickGui.catHeight.getValue(); // CHECK: tip Setting<Integer> ise float'a cast gerekebilir
            float gap = 4f;

            int count = 0;
            for (Module.Category c : Managers.MODULE.getCategories())
                if (c != Module.Category.HUD) count++;

            float totalWidth = count * moduleWidth + (count - 1) * gap;
            float startX = (mc().getWindow().getScaledWidth() - totalWidth) / 2f;
            float y = 20f;

            float offset = 0f;
            for (Module.Category c : Managers.MODULE.getCategories()) {
                if (c == Module.Category.HUD) continue;

                Category window = new Category(
                        c,
                        Managers.MODULE.getModulesByCategory(c),
                        startX + offset,
                        y,
                        moduleWidth,
                        catHeight
                );
                window.setOpen(true);
                windows.add(window);
                offset += moduleWidth + gap;
            }
            firstOpen = false;
        }

        windows.forEach(AbstractCategory::restorePos);
        windows.forEach(AbstractCategory::init);

        if (mc().player != null) {
            prevYaw = mc().player.getYaw();
            prevPitch = mc().player.getPitch();
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

    // ---- render --------------------------------------------------------
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (fullNullCheck()) return;

        anyHovered = false;
        currentDescription = "";

        ClickGui cfg = ModuleManager.clickGui;

        // arka plan blur
        if (cfg.blur.getValue()) applyBlur(delta);

        // arka plan resmi (anime vs.)
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

        // kamera dönerken GUI hafifçe kayar (parallax)
        float parallaxX = 0, parallaxY = 0;
        if (mc().player != null) {
            parallaxX = (prevYaw - mc().player.getYaw()) * 0.4f;
            parallaxY = (prevPitch - mc().player.getPitch()) * 0.4f;
            parallaxX = Math.max(-12f, Math.min(12f, parallaxX));
            parallaxY = Math.max(-12f, Math.min(12f, parallaxY));
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

        matrices.translate(parallaxX, parallaxY + scrollY, 0);

        // pencereler: listenin sonundaki en üstte
        for (AbstractCategory w : windows)
            w.render(context, mouseX, mouseY, delta);

        matrices.pop();

        renderFooter(context, mouseX, mouseY);
        updateCursor();

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderImage(DrawContext context) {
        ClickGui cfg = ModuleManager.clickGui;
        if (cfg.image.getValue() == ClickGui.Image.None) return;

        double anim = cfg.imageAnimation.getValue() ? imageAnimation.getAnimationd() : 1.0;
        RenderSystem.setShaderTexture(0, cfg.image.getValue().file);

        double w = cfg.image.getValue().fileWidth;
        double h = cfg.image.getValue().fileHeight;
        double x = mc().getWindow().getScaledWidth() - w * anim;
        double y = mc().getWindow().getScaledHeight() - h;

        // CHECK: renderTexture(matrices, x, y, w, h, u, v, regionW, regionH, texW, texH)
        Render2DEngine.renderTexture(context.getMatrices(), x, y, w, h, 0, 0, w, h, w, h);
    }

    private void renderFooter(DrawContext context, int mouseX, int mouseY) {
        ClickGui cfg = ModuleManager.clickGui;
        MatrixStack ms = context.getMatrices();

        float centerX = mc().getWindow().getScaledWidth() / 2f;
        float bottomY = mc().getWindow().getScaledHeight() - 24f;

        // modül açıklaması
        if (cfg.descriptions.getValue() && !currentDescription.isEmpty()) {
            float tw = FontRenderers.sf_medium.getStringWidth(currentDescription);
            Render2DEngine.drawHudBase(ms, centerX - tw / 2f - 6f, bottomY - 20f, tw + 12f, 14f, 3f, false);
            FontRenderers.sf_medium.drawString(ms, currentDescription,
                    centerX - tw / 2f, bottomY - 16f, HudEditor.getColor(1).getRGB());
        }

        // ipuçları
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
            // Wayland'de cursor oluşturma sorun çıkarabiliyor
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
        // varsayılan karartma / blur'u kendimiz çiziyoruz
    }

    // ---- input ---------------------------------------------------------
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (ModuleManager.clickGui.scrollMode.getValue() == ClickGui.scrollModeEn.Old) {
            // eski mod: sadece imlecin altındaki kategori kayar
            windows.forEach(w -> w.setModuleOffset((float) verticalAmount * 5f, (float) mouseX, (float) mouseY));
        } else {
            // yeni mod: tüm GUI kayar
            scrollY += (float) verticalAmount * 10f;
            scrollY = Math.max(-400f, Math.min(0f, scrollY));
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double my = mouseY - scrollY;

        // tıklanan pencereyi öne al
        AbstractCategory clicked = null;
        for (int i = windows.size() - 1; i >= 0; i--) {
            AbstractCategory w = windows.get(i);
            if (Render2DEngine.isHovered(mouseX, my, w.getX(), w.getY(), w.getWidth(), w.getHeight())) {
                clicked = w;
                break;
            }
        }
        if (clicked != null && windows.get(windows.size() - 1) != clicked) {
            windows.remove(clicked);
            windows.add(clicked);
        }

        final double fmy = my;
        windows.forEach(w -> w.mouseClicked((int) mouseX, (int) fmy, button));
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        final double fmy = mouseY - scrollY;
        windows.forEach(w -> w.mouseReleased((int) mouseX, (int) fmy, button));
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        windows.forEach(w -> w.charTyped(chr, modifiers));
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // bind/arama kutusu gibi elemanlar tuşu tüketebilir
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

        windows.forEach(AbstractCategory::savePos);
        windows.forEach(AbstractCategory::onClose);

        if (handActive) {
            GLFW.glfwSetCursor(mc().getWindow().getHandle(), 0L);
            handActive = false;
        }

        if (ModuleManager.clickGui.closeAnimation.getValue()) {
            close = true;
            closeAnimation = 0;
            closeDirectionX = Math.random() < 0.5 ? -1 : 1;
            closeDirectionY = Math.random() < 0.5 ? -1 : 1;
        } else {
            close = true;
            super.close();
        }
    }

    // ---- helpers -------------------------------------------------------
    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }
}
