package com.tmbu.tmbuclient.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.ArrayList;
import java.util.List;

/**
 * A VertexConsumer that captures quad vertex positions from entity model rendering.
 * Every 4 vertices form a quad. We store the positions and later draw them as
 * wireframe lines or filled triangles.
 */
public class QuadCapturingVertexConsumer implements VertexConsumer {
    private final List<float[]> vertices = new ArrayList<>();

    public List<float[]> getVertices() { return vertices; }
    public int getQuadCount() { return vertices.size() / 4; }

    public void clear() { vertices.clear(); }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        vertices.add(new float[]{x, y, z});
        return this;
    }

    // All other vertex attributes are ignored — we only need positions
    @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
    @Override public VertexConsumer setColor(int argb) { return this; }
    @Override public VertexConsumer setUv(float u, float v) { return this; }
    @Override public VertexConsumer setUv1(int u, int v) { return this; }
    @Override public VertexConsumer setUv2(int u, int v) { return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    @Override public VertexConsumer setLineWidth(float w) { return this; }
}
