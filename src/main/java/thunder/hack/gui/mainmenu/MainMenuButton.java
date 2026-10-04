package thunder.hack.gui.mainmenu;

import net.minecraft.client.gui.DrawContext;
import org.jetbrains.annotations.NotNull;
import thunder.hack.gui.font.FontRenderers;
import thunder.hack.utility.render.Render2DEngine;

import static thunder.hack.features.modules.Module.mc;

public class MainMenuButton {
    public static final float WIDTH = 150f;
    public static final float HEIGHT = 28f;
    public static final float RIGHT_MARGIN = 70f;

    private final float posY;
    private final String name;
    private final Runnable action;

    // posY: ekranin dikey ortasina gore konum
    public MainMenuButton(float posY, @NotNull String name, Runnable action) {
        this.posY = posY;
        this.name = name;
        this.action = action;
    }

    private float getX() {
        return mc.getWindow().getScaledWidth() - RIGHT_MARGIN - WIDTH;
    }

    private float getY() {
        return mc.getWindow().getScaledHeight() / 2f + posY;
    }

    public void onRender(DrawContext context, float mouseX, float mouseY) {
        float x = getX();
        float y = getY();
        Render2DEngine.drawHudBase(context.getMatrices(), x, y, WIDTH, HEIGHT, 8);
        boolean hovered = Render2DEngine.isHovered(mouseX, mouseY, x, y, WIDTH, HEIGHT);
        FontRenderers.monsterrat.drawCenteredString(context.getMatrices(), name, x + WIDTH / 2f, y + HEIGHT / 2f - 3f, hovered ? -1 : Render2DEngine.applyOpacity(-1, 0.7f));
    }

    public void onClick(int mouseX, int mouseY) {
        boolean hovered = Render2DEngine.isHovered(mouseX, mouseY, getX(), getY(), WIDTH, HEIGHT);
        if (hovered) action.run();
    }
}
