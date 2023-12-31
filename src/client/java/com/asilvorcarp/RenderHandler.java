package com.asilvorcarp;

import com.mojang.blaze3d.systems.RenderSystem;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.Color4f;
import fi.dy.masa.malilib.util.EntityUtils;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;
import org.jetbrains.annotations.NotNull;
import org.joml.*;

import java.lang.Math;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.asilvorcarp.ApexMC.LOGGER;
import static com.asilvorcarp.ApexMC.Vec3dToVector3d;
import static com.asilvorcarp.ApexMCClient.pingKeyBinding;

public class RenderHandler implements IRenderer {
    public static final boolean DEBUG = false;
    private static final RenderHandler INSTANCE = new RenderHandler();
    private final MinecraftClient mc;
    // the count for debug
    public int debug_count;
    // safe for multi-thread
    public HashMap<String, CopyOnWriteArrayList<PingPoint>> pings;
    // the ping that is pointed to
    private PingPoint onPing;

    private static final Identifier PING_BASIC = new Identifier(ApexMC.MOD_ID, "textures/ping/ping_basic.png");

    public RenderHandler() {
        this.mc = MinecraftClient.getInstance();
        this.debug_count = 0;
        this.pings = new HashMap<>();
        this.onPing = null;
    }

    public static RenderHandler getInstance() {
        return INSTANCE;
    }

    public void addPing(PingPoint p) {
        if (pings.get(p.owner) == null) {
            var list = new CopyOnWriteArrayList<PingPoint>();
            list.add(p);
            pings.put(p.owner, list);
        } else {
            var pingList = pings.get(p.owner);
            if (pingList.size() >= ModConfig.pingNumEach) {
                pingList.subList(0, pingList.size() - ModConfig.pingNumEach + 1).clear();
            }
            pingList.add(p);
        }
    }

    public PingPoint getOnPing() {
        return onPing;
    }

    public boolean isOnPing() {
        return onPing != null;
    }

    @Override
    public void onRenderWorldLast(MatrixStack matrixStack, net.minecraft.util.math.Matrix4f projMatrix) {
        if (this.mc.world != null && this.mc.player != null && !this.mc.options.hudHidden) {
            this.renderOverlays(matrixStack, projMatrix, this.mc);
        }
    }

    public static Quaternionf getDegreesQuaternion(Vector3f vector, float degrees) {
        return new Quaternionf().fromAxisAngleDeg(vector, degrees);
    }

    private static Vec3d map(double anglePerPixel, Vec3d cameraDir, Vector3f horizontalRotationAxis,
                             Vector3f verticalRotationAxis, int x, int y, int width, int height) {
        float horizontalRotation = (float) ((x - width / 2f) * anglePerPixel);
        float verticalRotation = (float) ((y - height / 2f) * anglePerPixel);

        final Vector3f temp2 = ApexMC.Vec3dToV3f(cameraDir);
        Quaternionfc rot1 = getDegreesQuaternion(verticalRotationAxis, verticalRotation);
        Quaternionfc rot2 = getDegreesQuaternion(horizontalRotationAxis, horizontalRotation);
        temp2.rotate(rot1);
        temp2.rotate(rot2);
        return new Vec3d(Vector3fToVec3f(temp2));
    }

    private static Vec3f Vector3fToVec3f(Vector3f v) {
        return new Vec3f(v.x, v.y, v.z);
    }

    @Override
    public void onRenderGameOverlayPost(MatrixStack matrixStack) {
        boolean setOnPing = false;
        for (var entry : this.pings.entrySet()) {
            var owner = entry.getKey();
            for (var ping : entry.getValue()) {
                MinecraftClient client = MinecraftClient.getInstance();
                int width = client.getWindow().getScaledWidth();
                int height = client.getWindow().getScaledHeight();
                assert client.cameraEntity != null;
                Vec3d cameraPos = client.cameraEntity.getPos();
                Vec3d targetPos = ping.pos;
                Vec3d cameraDirection = client.cameraEntity.getRotationVec(1.0f);
                // get real fly/sprint fov
                double fov = client.options.getFov().getValue();
                if (client.player != null) {
                    fov *= client.player.getFovMultiplier();
                }
                // get the icon center on screen
                Vector2d v2 = getIconCenter(width, height, cameraDirection, fov, cameraPos, targetPos);
                // render the icon
                renderIconHUD(v2.x, v2.y, ping);
                // render info if pointing at it (and don't do twice)
                if (!setOnPing) {
                    var mid = new Vector2d((double) width / 2, (double) height / 2);
                    var fromMid = v2.sub(mid);
                    if (fromMid.length() <= mid.length() / 25) {
                        renderInfoHUD((int) (width / 2.0 + 5), (int) (height / 2.0 + 5), ping, matrixStack);
                        // set lookingAtPing
                        onPing = ping;
                        setOnPing = true;
                    }
                }
            }
        }
        if (!setOnPing) {
            onPing = null;
        }
    }

    public static Vec3d XY2Vec3d(Vector2i xy) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        assert client.cameraEntity != null;
        Vec3d cameraDirection = client.cameraEntity.getRotationVec(1.0f);
        double fov = client.options.getFov().getValue();
        double angleSize = fov / height;

        Vector3f verticalRotationAxis = ApexMC.Vec3dToV3f(cameraDirection);
        verticalRotationAxis.cross(new Vector3f(0, 1, 0));
        verticalRotationAxis.normalize();
        Vector3f horizontalRotationAxis = ApexMC.Vec3dToV3f(cameraDirection);
        horizontalRotationAxis.cross(verticalRotationAxis);
        horizontalRotationAxis.normalize();
        verticalRotationAxis = ApexMC.Vec3dToV3f(cameraDirection);
        verticalRotationAxis.cross(horizontalRotationAxis);
        cameraDirection.normalize();

        return map(angleSize, cameraDirection, horizontalRotationAxis, verticalRotationAxis, xy.x, xy.y, width, height);
    }

    @NotNull
    private Vector2d getIconCenter(int width, int height, Vec3d cameraDir,
                                   double fov, Vec3d cameraPos, Vec3d targetPos) {
        double halfWidth = width / 2.0, halfHeight = height / 2.0;
        Matrix4d viewMatrix = new Matrix4d();
        // eye position
        Vector3d eyeVector = Vec3dToVector3d(cameraPos);
        // the center it is looking at (world coordinates)
        Vector3d centerVector = Vec3dToVector3d(cameraPos.add(cameraDir));
        // 'up' in world space
        Vector3d upVector = new Vector3d(0, 1, 0);
        double angleCosAbs = Math.abs(Vec3dToVector3d(cameraDir).angleCos(upVector));
        if (angleCosAbs == 1.0) {
            if (DEBUG)
                System.out.println("fuck");
            // TODO fix NaN
        }
        // the look-at transformation
        viewMatrix.setLookAt(eyeVector, centerVector, upVector);
        Vector3d tarVector = Vec3dToVector3d(targetPos);
        // TODO but why???
        tarVector.y -= 1.618;
        Vector4d worldPositionVector = new Vector4d(tarVector, 1);
        Vector4d tarPosCamSpace = new Vector4d();
        viewMatrix.transform(worldPositionVector, tarPosCamSpace);
        tarPosCamSpace.div(tarPosCamSpace.w);
        // target position in camera space
        // increase when right up back

        if (DEBUG) {
            System.out.printf("fov: %.2f\n", fov);
            LOGGER.debug("tarPosCamSpace: " + tarPosCamSpace);
        }

        double aspectRatio = (float) width / height;
        double radFov = Math.toRadians(fov);
        double near = 0.1;
        double far = Double.POSITIVE_INFINITY;

        Matrix4d projectionMatrix = new Matrix4d().setPerspective(radFov, aspectRatio, near, far);
        Vector4d ndc = new Vector4d();
        projectionMatrix.transform(tarPosCamSpace, ndc);
        ndc.div(ndc.w);
        // the target is at back
        boolean atBack = Math.signum(tarPosCamSpace.z) == 1.0;

        if (DEBUG)
            System.out.printf("ndc: %.2f, %.2f, %.2f, %.2f\n", ndc.x, ndc.y, ndc.z, ndc.w);

        // NDC to screen space: sx, sy
        // the xyz from middle of screen, increase when right up forward
        double sx = ndc.x * halfWidth, sy = ndc.y * halfHeight;
        if (DEBUG)
            System.out.printf("sx sy: %.2f, %.2f\n", sx, sy);

        // the x,y from the middle of the screen, increase when right down
        double mx = sx, my = -sy;
        // if at back, move to border
        if (atBack) {
            var tempMx = halfWidth * -Math.signum(mx);
            my = my / mx * tempMx;
            mx = tempMx;
        }
        // limit to screen border
        // TODO add margin and arrow
        double xm_new = mx, ym_new = my;
        if (Math.abs(mx) > halfWidth) {
            xm_new = halfWidth * Math.signum(mx);
            ym_new = my / mx * xm_new;
        } else if (Math.abs(my) > halfHeight) {
            ym_new = halfHeight * Math.signum(my);
            xm_new = mx / my * ym_new;
        }
        return new Vector2d(xm_new + halfWidth, ym_new + halfHeight);
    }

    /**
     * cx, cy: center of the icon
     * ping: the PingPoint
     */
    private void renderIconHUD(double cx, double cy, PingPoint ping) {
        double zLevel = 0;
        var realResizer = ModConfig.iconSize / 32;
        float u = 0, v = 0, width = 256 * realResizer, height = 256 * realResizer;
        float pixelWidth = 0.00390625F / realResizer;

        double x = cx - width / 2;
        double y = cy - height / 2;

        // TODO add background
        RenderUtils.bindTexture(PING_BASIC);

        // the following is
        // RenderUtils.drawTexturedRect(0, 0, 0, 0, 128, 128);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.applyModelViewMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        RenderUtils.setupBlend();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        buffer.vertex(x, y + height, zLevel).texture(u * pixelWidth, (v + height) * pixelWidth).next();
        buffer.vertex(x + width, y + height, zLevel).texture((u + width) * pixelWidth, (v + height) * pixelWidth).next();
        buffer.vertex(x + width, y, zLevel).texture((u + width) * pixelWidth, v * pixelWidth).next();
        buffer.vertex(x, y, zLevel).texture(u * pixelWidth, v * pixelWidth).next();

        tessellator.draw();
    }

    private void renderInfoHUD(int topLeftX, int topLeftY, PingPoint ping, MatrixStack ms) {
        MinecraftClient client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null) {
            return;
        }
        List<String> textLines = new ArrayList<>();
        var playerPos = player.getPos();
        var dist = playerPos.distanceTo(ping.pos);
        textLines.add("%.0f m".formatted(dist));
        if (!ping.owner.equals(player.getEntityName())) {
            textLines.add("%s".formatted(ping.owner));
        }
        // render
        // reference: RenderUtils.renderText();
        TextRenderer textRenderer = client.textRenderer;
        for (String line : textLines) {
            textRenderer.drawWithShadow(ms, line, topLeftX, topLeftY, ModConfig.infoColor);
            topLeftY += textRenderer.fontHeight + 2;
        }
        String hotkey = humanReadableHotkey(pingKeyBinding);
        var keyIndicator = "Cancel (" + hotkey + ")";
        textRenderer.drawWithShadow(ms, keyIndicator, topLeftX, topLeftY, 0xFFFFFFFF);
    }

    @NotNull
    public static String humanReadableHotkey(KeyBinding keybinding) {
        var hotkeyPath = KeyBindingHelper.getBoundKeyOf(keybinding).toString().split("\\.");
        // enough for keyboard
        var hotkey = hotkeyPath[hotkeyPath.length - 1].toUpperCase();
        // add "m" for mouse
        if (hotkeyPath.length == 3 && hotkeyPath[1].equals("mouse")) {
            hotkey = "M" + hotkey;
        }
        return hotkey;
    }

    public void renderOverlays(MatrixStack matrixStack, net.minecraft.util.math.Matrix4f projMatrix,
                               MinecraftClient mc) {
        Entity entity = EntityUtils.getCameraEntity();

        if (entity == null) {
            return;
        }

        for (var entry : this.pings.entrySet()) {
            var owner = entry.getKey();
            var pingList = entry.getValue();
            // let it vanish
            pingList.removeIf(p -> p.shouldVanish(ModConfig.secondsToVanish));
            for (var ping : pingList) {
                highlightPing(ping, mc);
            }
        }
    }

    private static void highlightPing(PingPoint ping, MinecraftClient mc) {
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        double x = ping.pos.x - cameraPos.x;
        double y = ping.pos.y - cameraPos.y;
        double z = ping.pos.z - cameraPos.z;

        assert mc.player != null;
        double size = 0.3;
        double minX = x - size / 2;
        double minY = y - size / 2;
        double minZ = z - size / 2;
        double maxX = x + size / 2;
        double maxY = y + size / 2;
        double maxZ = z + size / 2;
        float r = ping.color.getRed(), g = ping.color.getGreen(), b = ping.color.getBlue();
        r /= 256;
        g /= 256;
        b /= 256;
        Color4f color = new Color4f(r, g, b);

        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.polygonOffset(-3f, -3f);
        RenderSystem.enablePolygonOffset();
        RenderUtils.setupBlend();
        RenderUtils.color(1f, 1f, 1f, 1f);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.applyModelViewMatrix();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        RenderUtils.drawBoxAllSidesBatchedQuads(minX, minY, minZ, maxX, maxY, maxZ, Color4f.fromColor(color, 0.3f), buffer);

        tessellator.draw();

        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        RenderUtils.drawBoxAllEdgesBatchedLines(minX, minY, minZ, maxX, maxY, maxZ, Color4f.fromColor(color, 1f), buffer);

        tessellator.draw();

        RenderSystem.polygonOffset(0f, 0f);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public void updateData(MinecraftClient mc) {
        // do nothing?
    }

    public void removeOnPing() {
        pings.get(onPing.owner).removeIf((p -> p.id.equals(onPing.id)));
    }

    public void removePing(PingPoint p) {
        pings.get(p.owner).removeIf((lhs -> lhs.id.equals(p.id)));
    }
}
