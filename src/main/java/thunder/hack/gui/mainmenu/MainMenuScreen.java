package thunder.hack.gui.mainmenu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import thunder.hack.api.IAddon;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.gui.font.FontRenderers;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.utility.render.TextureStorage;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static thunder.hack.features.modules.Module.mc;

public class MainMenuScreen extends Screen {
    private final List<MainMenuButton> buttons = new ArrayList<>();
    public boolean confirm = false;
    public static int ticksActive;

    protected MainMenuScreen() {
        super(Text.of("THMainMenuScreen"));
        INSTANCE = this;

        buttons.add(new MainMenuButton(-85, "Singleplayer", () -> mc.setScreen(new SelectWorldScreen(this))));
        buttons.add(new MainMenuButton(-49, "Multiplayer", () -> mc.setScreen(new MultiplayerScreen(this))));
        buttons.add(new MainMenuButton(-13, "Settings", () -> mc.setScreen(new OptionsScreen(this, mc.options))));
        buttons.add(new MainMenuButton(23, "ClickGUI", () -> ModuleManager.clickGui.setGui()));
        buttons.add(new MainMenuButton(71, "Quit", mc::scheduleStop));
    }

    private static MainMenuScreen INSTANCE = new MainMenuScreen();

    public static MainMenuScreen getInstance() {
        ticksActive = 0;

        if (INSTANCE == null) {
            INSTANCE = new MainMenuScreen();
        }
        return INSTANCE;
    }

    @Override
    public void tick() {
        ticksActive++;

        if (ticksActive > 400) {
            ticksActive = 0;
        }
    }

    @Override
    public void render(@NotNull DrawContext context, int mouseX, int mouseY, float delta) {
        float halfOfWidth = mc.getWindow().getScaledWidth() / 2f;
        float halfOfHeight = mc.getWindow().getScaledHeight() / 2f;

        renderBackground(context, mouseX, mouseY, delta);

        // Buyuk F logosu (ekranin sol-ortasi)
        int cx = (int) (mc.getWindow().getScaledWidth() * 0.30f);
        int cy = (int) halfOfHeight;
        int blue = new Color(55, 138, 221).getRGB();
        int x0 = cx - 45;
        context.fill(x0, cy - 65, x0 + 30, cy + 65, blue);
        context.fill(x0, cy - 65, x0 + 100, cy - 35, blue);
        context.fill(x0, cy - 15, x0 + 75, cy + 15, blue);

        buttons.forEach(b -> b.onRender(context, mouseX, mouseY));

        float colCenterX = mc.getWindow().getScaledWidth() - MainMenuButton.RIGHT_MARGIN - MainMenuButton.WIDTH / 2f;
        context.getMatrices().push();
        context.getMatrices().translate(colCenterX, halfOfHeight - 130f, 0);
        context.getMatrices().scale(1.8f, 1.8f, 1f);
        FontRenderers.sf_bold.drawCenteredString(context.getMatrices(), "FelixDlc", 0, 0, blue);
        FontRenderers.sf_bold.drawCenteredString(context.getMatrices(), "Client", 0, 11, blue);
        context.getMatrices().pop();

        boolean hovered = Render2DEngine.isHovered(mouseX, mouseY, colCenterX - 50, halfOfHeight + 110, 100, 10);
        FontRenderers.sf_medium.drawCenteredString(context.getMatrices(), "<-- Back to default menu", colCenterX, halfOfHeight + 110, hovered ? -1 : Render2DEngine.applyOpacity(-1, 0.6f));

        onlineText:
        {
            String onlineUsers = String.format("online: %s%s", Formatting.DARK_GREEN, Managers.TELEMETRY.getOnlinePlayers().size());

            FontRenderers.sf_bold.drawCenteredString(context.getMatrices(), onlineUsers, halfOfWidth, halfOfHeight * 2 - 15, Color.GREEN);

            context.getMatrices().push();
            context.getMatrices().translate(halfOfWidth - 10 - FontRenderers.sf_medium.getStringWidth(onlineUsers) / 2f, halfOfHeight * 2 - 17, 0);
            Render2DEngine.drawBloom(context.getMatrices(), Render2DEngine.applyOpacity(Color.GREEN, 0.6f), 9f);
            context.getMatrices().pop();

            context.getMatrices().push();
            context.getMatrices().translate(halfOfWidth - 10 - FontRenderers.sf_medium.getStringWidth(onlineUsers) / 2f, halfOfHeight * 2 - 17, 0);
            Render2DEngine.drawBloom(context.getMatrices(), Render2DEngine.applyOpacity(Color.GREEN, (float) (0.5f + (Math.sin((double) System.currentTimeMillis() / 500)) / 2f)), 9f);
            context.getMatrices().pop();

        }

        Render2DEngine.drawHudBase(context.getMatrices(), mc.getWindow().getScaledWidth() - 40, mc.getWindow().getScaledHeight() - 40, 30, 30, 5, Render2DEngine.isHovered(mouseX, mouseY, mc.getWindow().getScaledWidth() - 40, mc.getWindow().getScaledHeight() - 40, 30, 30) ? 0.7f : 1f);
        RenderSystem.setShaderColor(1f, 1f, 1f, Render2DEngine.isHovered(mouseX, mouseY, mc.getWindow().getScaledWidth() - 40, mc.getWindow().getScaledHeight() - 40, 30, 30) ? 0.7f : 1f);
        context.drawTexture(TextureStorage.thTeam, mc.getWindow().getScaledWidth() - 40, mc.getWindow().getScaledHeight() - 40, 30, 30, 0, 0, 30, 30, 30, 30);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        int totalAddonsLoaded = Managers.ADDON.getTotalAddons();
        String addonsText = "Addons Loaded: " + totalAddonsLoaded;
        int screenWidth = mc.getWindow().getScaledWidth();
        int textWidth = (int) FontRenderers.sf_bold.getStringWidth(addonsText);
        int textX = screenWidth - textWidth - 5;
        FontRenderers.sf_bold.drawString(context.getMatrices(), addonsText, textX, 5, Color.WHITE.getRGB());

        int offset = 0;
        for (IAddon addon : Managers.ADDON.getAddons()) {
            // for (String addon : Arrays.asList("Addon", "Addon2", "Addon3", "Addon4", "Addon5")) {
            textWidth = (int) FontRenderers.sf_bold.getStringWidth(addon.getName() + " |");
            textX = screenWidth - textWidth - 5;
            FontRenderers.sf_bold.drawString(context.getMatrices(), addon.getName() + Formatting.WHITE + " |", textX, 13 + offset, Color.GRAY.getRGB());
            offset += 9;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float halfOfWidth = mc.getWindow().getScaledWidth() / 2f;
        float halfOfHeight = mc.getWindow().getScaledHeight() / 2f;
        buttons.forEach(b -> b.onClick((int) mouseX, (int) mouseY));

        float colCenterX = mc.getWindow().getScaledWidth() - MainMenuButton.RIGHT_MARGIN - MainMenuButton.WIDTH / 2f;
        if (Render2DEngine.isHovered(mouseX, mouseY, colCenterX - 50, halfOfHeight + 110, 100, 10)) {
            confirm = true;
            mc.setScreen(new TitleScreen());
            confirm = false;
        }

        if (Render2DEngine.isHovered(mouseX, mouseY, mc.getWindow().getScaledWidth() - 40, mc.getWindow().getScaledHeight() - 40, 40, 40))
            mc.setScreen(CreditsScreen.getInstance());

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
