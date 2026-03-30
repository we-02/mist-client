package com.tmbu.tmbuclient.hud;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

/**
 * Anchor-based positioning box for HUD elements.
 * Stores position relative to an anchor point so elements stay in place on window resize.
 */
public class HudBox {
    private final HudElement element;

    public XAnchor xAnchor = XAnchor.Left;
    public YAnchor yAnchor = YAnchor.Top;

    public int x, y;
    int width, height;

    public HudBox(HudElement element) {
        this.element = element;
    }

    public void setSize(double width, double height) {
        if (width >= 0) this.width = (int) Math.ceil(width);
        if (height >= 0) this.height = (int) Math.ceil(height);
    }

    public void setPos(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void updateAnchors() {
        setXAnchor(getXAnchor(getRenderX()));
        setYAnchor(getYAnchor(getRenderY()));
    }

    public void setXAnchor(XAnchor anchor) {
        if (xAnchor != anchor) {
            int renderX = getRenderX();
            int sw = screenWidth();
            switch (anchor) {
                case Left -> x = renderX;
                case Center -> x = renderX + width / 2 - sw / 2;
                case Right -> x = renderX + width - sw;
            }
            xAnchor = anchor;
        }
    }

    public void setYAnchor(YAnchor anchor) {
        if (yAnchor != anchor) {
            int renderY = getRenderY();
            int sh = screenHeight();
            switch (anchor) {
                case Top -> y = renderY;
                case Center -> y = renderY + height / 2 - sh / 2;
                case Bottom -> y = renderY + height - sh;
            }
            yAnchor = anchor;
        }
    }

    public void move(int deltaX, int deltaY) {
        x += deltaX;
        y += deltaY;

        if (element.autoAnchors) updateAnchors();

        int border = 4;
        if (xAnchor == XAnchor.Left && x < border) x = border;
        else if (xAnchor == XAnchor.Right && x > -border) x = -border;
        if (yAnchor == YAnchor.Top && y < border) y = border;
        else if (yAnchor == YAnchor.Bottom && y > -border) y = -border;
    }

    public XAnchor getXAnchor(double rx) {
        double third = screenWidth() / 3.0;
        boolean left = rx <= third;
        boolean right = rx + width >= third * 2;
        if ((left && right) || (!left && !right)) return XAnchor.Center;
        return left ? XAnchor.Left : XAnchor.Right;
    }

    public YAnchor getYAnchor(double ry) {
        double third = screenHeight() / 3.0;
        boolean top = ry <= third;
        boolean bottom = ry + height >= third * 2;
        if ((top && bottom) || (!top && !bottom)) return YAnchor.Center;
        return top ? YAnchor.Top : YAnchor.Bottom;
    }

    public int getRenderX() {
        int sw = screenWidth();
        return switch (xAnchor) {
            case Left -> x;
            case Center -> sw / 2 - width / 2 + x;
            case Right -> sw - width + x;
        };
    }

    public int getRenderY() {
        int sh = screenHeight();
        return switch (yAnchor) {
            case Top -> y;
            case Center -> sh / 2 - height / 2 + y;
            case Bottom -> sh - height + y;
        };
    }

    public double alignX(double selfWidth, double contentWidth, Alignment alignment) {
        XAnchor a = xAnchor;
        if (alignment == Alignment.Left) a = XAnchor.Left;
        else if (alignment == Alignment.Center) a = XAnchor.Center;
        else if (alignment == Alignment.Right) a = XAnchor.Right;
        return switch (a) {
            case Left -> 0;
            case Center -> selfWidth / 2.0 - contentWidth / 2.0;
            case Right -> selfWidth - contentWidth;
        };
    }

    // Serialization

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("xAnchor", xAnchor.name());
        obj.addProperty("yAnchor", yAnchor.name());
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        return obj;
    }

    public void fromJson(JsonObject obj) {
        if (obj.has("xAnchor")) {
            try { xAnchor = XAnchor.valueOf(obj.get("xAnchor").getAsString()); } catch (Exception ignored) {}
        }
        if (obj.has("yAnchor")) {
            try { yAnchor = YAnchor.valueOf(obj.get("yAnchor").getAsString()); } catch (Exception ignored) {}
        }
        if (obj.has("x")) x = obj.get("x").getAsInt();
        if (obj.has("y")) y = obj.get("y").getAsInt();
    }

    private static int screenWidth() {
        var w = Minecraft.getInstance().getWindow();
        return w != null ? w.getGuiScaledWidth() : 1920;
    }

    private static int screenHeight() {
        var w = Minecraft.getInstance().getWindow();
        return w != null ? w.getGuiScaledHeight() : 1080;
    }
}
