package com.heytozzz.htzcosmetics.studio;

import com.heytozzz.htzcosmetics.core.model.Bone;
import com.heytozzz.htzcosmetics.core.model.CosmeticModel;
import com.heytozzz.htzcosmetics.core.model.Cube;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Tiny software renderer for cosmetic models (rest pose, no animation): orthographic view, painter's algorithm,
 * and the vanilla "box UV" face layout, so the thumbnails look like the in-game ones. Display only; it never
 * validates anything.
 */
final class ModelRenderer {
    private record Face(double[][] screen, double depth, double light, double[] uv) {
    }

    private ModelRenderer() {
    }

    /** Pre-computed geometry of a model in model space (pixels, Y down). */
    static final class Mesh {
        final List<double[][]> quads = new ArrayList<>(); // 4 corners (x, y, z)
        final List<double[]> uvs = new ArrayList<>();     // u1, v1, u2, v2 (texture units)
        final List<double[]> centers = new ArrayList<>(); // centre of the owning cube
        final double[] center = new double[3];
        double radius = 8;
        final int texW;
        final int texH;

        Mesh(CosmeticModel model) {
            texW = model.textureWidth();
            texH = model.textureHeight();
            List<Bone> bones = model.bones();
            double[][] matrices = new double[bones.size()][];
            double[] min = {1e9, 1e9, 1e9};
            double[] max = {-1e9, -1e9, -1e9};
            for (int i = 0; i < bones.size(); i++) {
                Bone b = bones.get(i);
                double[] local = mul(translate(b.pivot()), rotationZYX(b.rotation()));
                matrices[i] = b.parentIndex() >= 0 ? mul(matrices[b.parentIndex()], local) : local;
                for (Cube c : b.cubes()) addCube(matrices[i], c, min, max);
            }
            if (quads.isEmpty()) return;
            double r = 0;
            for (int a = 0; a < 3; a++) center[a] = (min[a] + max[a]) / 2;
            for (double[][] q : quads) {
                for (double[] p : q) {
                    double dx = p[0] - center[0], dy = p[1] - center[1], dz = p[2] - center[2];
                    r = Math.max(r, Math.sqrt(dx * dx + dy * dy + dz * dz));
                }
            }
            radius = Math.max(1, r);
        }

        private void addCube(double[] m, Cube c, double[] min, double[] max) {
            double g = c.inflate();
            double x0 = c.origin()[0] - g, y0 = c.origin()[1] - g, z0 = c.origin()[2] - g;
            double x1 = c.origin()[0] + c.size()[0] + g, y1 = c.origin()[1] + c.size()[1] + g;
            double z1 = c.origin()[2] + c.size()[2] + g;
            if (c.mirror()) {
                double t = x0;
                x0 = x1;
                x1 = t;
            }
            double[][] v = {
                {x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0},
                {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            for (int i = 0; i < 8; i++) {
                v[i] = apply(m, v[i]);
                for (int a = 0; a < 3; a++) {
                    min[a] = Math.min(min[a], v[i][a]);
                    max[a] = Math.max(max[a], v[i][a]);
                }
            }
            double w = c.size()[0], h = c.size()[1], d = c.size()[2];
            double u = c.u(), vv = c.v();
            double f4 = u, f5 = u + d, f6 = u + d + w, f7 = u + d + w + w, f8 = u + d + w + d, f9 = u + d + w + d + w;
            double f10 = vv, f11 = vv + d, f12 = vv + d + h;
            double[] cc = {0, 0, 0};
            for (double[] p : v) for (int a = 0; a < 3; a++) cc[a] += p[a] / 8;
            // Same faces and UV rectangles as vanilla ModelPart.Cube.
            face(v, cc, new int[]{5, 4, 0, 1}, f5, f10, f6, f11); // down (model -Y = top in game)
            face(v, cc, new int[]{2, 3, 7, 6}, f6, f11, f7, f10); // up
            face(v, cc, new int[]{0, 4, 7, 3}, f4, f11, f5, f12); // west
            face(v, cc, new int[]{1, 0, 3, 2}, f5, f11, f6, f12); // north (front)
            face(v, cc, new int[]{5, 1, 2, 6}, f6, f11, f8, f12); // east
            face(v, cc, new int[]{4, 5, 6, 7}, f8, f11, f9, f12); // south (back)
        }

        private void face(double[][] v, double[] cubeCenter, int[] idx, double u1, double v1, double u2, double v2) {
            if (u1 == u2 || v1 == v2) return; // zero-area face (flat plane edge)
            quads.add(new double[][]{v[idx[0]], v[idx[1]], v[idx[2]], v[idx[3]]});
            uvs.add(new double[]{u1, v1, u2, v2});
            centers.add(cubeCenter);
        }
    }

    /**
     * Draws the mesh centred in the given box.
     *
     * @param yaw   degrees around the vertical axis (0 = front)
     * @param pitch degrees, positive looks from above
     */
    static void draw(Graphics2D g, Mesh mesh, BufferedImage texture, int x, int y, int w, int h,
                     double yaw, double pitch, double zoom) {
        if (mesh.quads.isEmpty()) return;
        double[] view = mul(rotationX(Math.toRadians(pitch)), rotationY(Math.toRadians(yaw)));
        double scale = Math.min(w, h) * 0.46 / mesh.radius * zoom;
        double cx = x + w / 2.0, cy = y + h / 2.0;
        double texScale = texture == null ? 1 : (double) texture.getWidth() / mesh.texW;

        List<Face> faces = new ArrayList<>();
        for (int i = 0; i < mesh.quads.size(); i++) {
            double[][] q = mesh.quads.get(i);
            double[][] s = new double[4][];
            double depth = 0;
            double[] fc = {0, 0, 0};
            for (int k = 0; k < 4; k++) {
                double[] p = {q[k][0] - mesh.center[0], q[k][1] - mesh.center[1], q[k][2] - mesh.center[2]};
                for (int a = 0; a < 3; a++) fc[a] += q[k][a] / 4;
                double[] r = apply(view, p);
                s[k] = new double[]{cx + r[0] * scale, cy + r[1] * scale};
                depth += r[2] / 4;
            }
            // Outward normal from the cube centre (robust for mirrored and rotated cubes).
            double[] cc = mesh.centers.get(i);
            double[] n = {fc[0] - cc[0], fc[1] - cc[1], fc[2] - cc[2]};
            double len = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            double light = 0.85;
            if (len > 1e-6) {
                for (int a = 0; a < 3; a++) n[a] /= len;
                double[] vn = apply(view, n);
                if (vn[2] > 1e-3) continue; // faces away from the camera
                // Minecraft-like fixed shading: top brightest, sides darker, bottom darkest.
                light = 0.6 * n[0] * n[0] + 0.8 * n[2] * n[2] + (n[1] < 0 ? 1.0 : 0.5) * n[1] * n[1];
            }
            faces.add(new Face(s, depth, light, mesh.uvs.get(i)));
        }
        faces.sort((a, b) -> Double.compare(b.depth(), a.depth())); // far first

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        Shape baseClip = g2.getClip();
        for (Face f : faces) {
            double[][] p = f.screen();
            Path2D poly = grow(p, 0.6);
            g2.setClip(baseClip);
            g2.clip(poly);
            double u1 = f.uv()[0] * texScale, v1 = f.uv()[1] * texScale;
            double u2 = f.uv()[2] * texScale, v2 = f.uv()[3] * texScale;
            if (texture != null) {
                // Affine map texture -> screen: p1 = (u1, v1), p0 = (u2, v1), p2 = (u1, v2).
                double ax = (p[0][0] - p[1][0]) / (u2 - u1), ay = (p[0][1] - p[1][1]) / (u2 - u1);
                double bx = (p[2][0] - p[1][0]) / (v2 - v1), by = (p[2][1] - p[1][1]) / (v2 - v1);
                AffineTransform at = new AffineTransform(ax, ay, bx, by,
                    p[1][0] - u1 * ax - v1 * bx, p[1][1] - u1 * ay - v1 * by);
                if (Math.abs(at.getDeterminant()) > 1e-9) g2.drawImage(texture, at, null);
            } else {
                g2.setColor(Theme.TEXT_DIM);
                g2.fill(poly);
            }
            if (f.light() < 0.999) {
                g2.setColor(new Color(0, 0, 0, (int) Math.round(Math.min(1, 1 - f.light()) * 255)));
                g2.fill(poly);
            }
        }
        g2.dispose();
    }

    /** The quad grown a little around its centre, so neighbouring faces do not leave hairline gaps. */
    private static Path2D grow(double[][] p, double px) {
        double cx = 0, cy = 0;
        for (double[] q : p) {
            cx += q[0] / 4;
            cy += q[1] / 4;
        }
        Path2D path = new Path2D.Double();
        for (int k = 0; k < 4; k++) {
            double dx = p[k][0] - cx, dy = p[k][1] - cy;
            double len = Math.max(1e-6, Math.sqrt(dx * dx + dy * dy));
            double x = p[k][0] + dx / len * px, y = p[k][1] + dy / len * px;
            if (k == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.closePath();
        return path;
    }

    // ---- 4x4 matrices (row-major, 16 doubles) ----

    private static double[] identity() {
        return new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    private static double[] translate(double[] t) {
        double[] m = identity();
        m[3] = t[0];
        m[7] = t[1];
        m[11] = t[2];
        return m;
    }

    private static double[] rotationX(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[]{1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1};
    }

    private static double[] rotationY(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[]{c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1};
    }

    private static double[] rotationZ(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[]{c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    /** Same order as vanilla ModelPart (Quaternionf.rotationZYX): Rz * Ry * Rx. */
    private static double[] rotationZYX(double[] degrees) {
        return mul(rotationZ(Math.toRadians(degrees[2])),
            mul(rotationY(Math.toRadians(degrees[1])), rotationX(Math.toRadians(degrees[0]))));
    }

    private static double[] mul(double[] a, double[] b) {
        double[] r = new double[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += a[i * 4 + k] * b[k * 4 + j];
                r[i * 4 + j] = s;
            }
        }
        return r;
    }

    private static double[] apply(double[] m, double[] p) {
        return new double[]{
            m[0] * p[0] + m[1] * p[1] + m[2] * p[2] + m[3],
            m[4] * p[0] + m[5] * p[1] + m[6] * p[2] + m[7],
            m[8] * p[0] + m[9] * p[1] + m[10] * p[2] + m[11]};
    }
}
