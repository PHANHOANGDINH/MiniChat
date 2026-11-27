package chat.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

/** Bộ biểu tượng vẽ bằng Java2D trên hệ toạ độ 24x24 (không dùng ảnh ngoài). */
public final class Icons {
    private Icons() { }

    public enum Kind {
        IMAGE, CLIP, MIC, STOP, SEND, PLUS, SEARCH, PLAY, DOWNLOAD, CLOSE, FILE,
        LEAVE, HASH, GLOBE, CHAT, USER, LOCK, SERVER, CHEVRON_DOWN, CHEVRON_UP,
        USERS, USER_PLUS, CAMERA, EDIT, INFO, COMPASS, CHECK, CROWN, TRASH, SMILE, STICKER
    }

    /** Vẽ biểu tượng vào ô vuông (x, y, size, size). */
    public static void draw(Graphics2D g0, Kind k, double x, double y, double size, Color c) {
        Graphics2D g = Theme.aa(g0);
        g.translate(x, y);
        g.scale(size / 24.0, size / 24.0);
        g.setColor(c);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (k) {
            case IMAGE -> {
                g.draw(Theme.rr(3, 3, 18, 18, 4));
                g.fill(new Ellipse2D.Double(7, 7, 3.4, 3.4));
                Path2D p = new Path2D.Double();
                p.moveTo(21, 15); p.lineTo(16, 10); p.lineTo(5, 21);
                g.draw(p);
            }
            case CLIP -> {
                g.rotate(Math.toRadians(30), 12, 12);
                Path2D p = new Path2D.Double();
                p.moveTo(10, 15);
                p.lineTo(10, 7);
                p.append(new Arc2D.Double(10, 4, 6, 6, 180, -180, Arc2D.OPEN), true);
                p.lineTo(16, 16);
                p.append(new Arc2D.Double(8, 12, 8, 8, 0, -180, Arc2D.OPEN), true);
                p.lineTo(8, 8);
                g.draw(p);
            }
            case MIC -> {
                g.draw(Theme.rr(9, 2.5, 6, 12, 3));
                g.draw(new Arc2D.Double(5, 5, 14, 14, 180, 180, Arc2D.OPEN));
                g.draw(new Line2D.Double(12, 19, 12, 22));
                g.draw(new Line2D.Double(8.5, 22, 15.5, 22));
            }
            case STOP -> g.fill(Theme.rr(6, 6, 12, 12, 3));
            case SEND -> {
                Path2D p = new Path2D.Double();
                p.moveTo(21.5, 2.5); p.lineTo(14, 21.5); p.lineTo(10.6, 13.4); p.lineTo(2.5, 10);
                p.closePath();
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.fill(p);
                g.draw(p);
            }
            case PLUS -> {
                g.draw(new Line2D.Double(12, 5, 12, 19));
                g.draw(new Line2D.Double(5, 12, 19, 12));
            }
            case SEARCH -> {
                g.draw(new Ellipse2D.Double(4, 4, 12.5, 12.5));
                g.draw(new Line2D.Double(15, 15, 20, 20));
            }
            case PLAY -> {
                Path2D p = new Path2D.Double();
                p.moveTo(8, 5.5); p.lineTo(18.5, 12); p.lineTo(8, 18.5); p.closePath();
                g.fill(p);
                g.draw(p);
            }
            case DOWNLOAD -> {
                g.draw(new Line2D.Double(12, 4, 12, 15));
                Path2D p = new Path2D.Double();
                p.moveTo(7, 10.5); p.lineTo(12, 15.5); p.lineTo(17, 10.5);
                g.draw(p);
                g.draw(new Line2D.Double(5, 20, 19, 20));
            }
            case CLOSE -> {
                g.draw(new Line2D.Double(6, 6, 18, 18));
                g.draw(new Line2D.Double(18, 6, 6, 18));
            }
            case FILE -> {
                Path2D p = new Path2D.Double();
                p.moveTo(6, 2.5); p.lineTo(14, 2.5); p.lineTo(19, 7.5); p.lineTo(19, 21.5);
                p.lineTo(6, 21.5); p.closePath();
                g.draw(p);
                Path2D f = new Path2D.Double();
                f.moveTo(14, 2.5); f.lineTo(14, 7.5); f.lineTo(19, 7.5);
                g.draw(f);
                g.draw(new Line2D.Double(9.5, 13, 15.5, 13));
                g.draw(new Line2D.Double(9.5, 17, 15.5, 17));
            }
            case LEAVE -> {
                Path2D p = new Path2D.Double();
                p.moveTo(10, 3.5); p.lineTo(5, 3.5); p.lineTo(5, 20.5); p.lineTo(10, 20.5);
                g.draw(p);
                g.draw(new Line2D.Double(10, 12, 20, 12));
                Path2D a = new Path2D.Double();
                a.moveTo(16, 8); a.lineTo(20, 12); a.lineTo(16, 16);
                g.draw(a);
            }
            case HASH -> {
                g.draw(new Line2D.Double(4, 9, 20, 9));
                g.draw(new Line2D.Double(4, 15, 20, 15));
                g.draw(new Line2D.Double(10, 3, 8, 21));
                g.draw(new Line2D.Double(16, 3, 14, 21));
            }
            case GLOBE -> {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.draw(new Ellipse2D.Double(8, 3, 8, 18));
                g.draw(new Line2D.Double(3, 12, 21, 12));
            }
            case CHAT -> {
                g.fill(Theme.rr(2.5, 3.5, 19, 14.5, 6));
                Path2D t = new Path2D.Double();
                t.moveTo(7, 16); t.lineTo(7, 21.5); t.lineTo(13, 16); t.closePath();
                g.fill(t);
            }
            case USER -> {
                g.draw(new Ellipse2D.Double(8, 3.5, 8, 8));
                g.draw(new Arc2D.Double(4.5, 14, 15, 14, 0, 180, Arc2D.OPEN));
            }
            case LOCK -> {
                g.draw(Theme.rr(5, 11, 14, 10, 3));
                g.draw(new Arc2D.Double(8, 3.5, 8, 15, 0, 180, Arc2D.OPEN));
            }
            case SERVER -> {
                g.draw(Theme.rr(3, 4, 18, 7, 2.5));
                g.draw(Theme.rr(3, 13, 18, 7, 2.5));
                g.fill(new Ellipse2D.Double(6.5, 6.5, 2, 2));
                g.fill(new Ellipse2D.Double(6.5, 15.5, 2, 2));
            }
            case CHEVRON_DOWN -> {
                Path2D p = new Path2D.Double();
                p.moveTo(6, 9); p.lineTo(12, 15); p.lineTo(18, 9);
                g.draw(p);
            }
            case CHEVRON_UP -> {
                Path2D p = new Path2D.Double();
                p.moveTo(6, 15); p.lineTo(12, 9); p.lineTo(18, 15);
                g.draw(p);
            }
            case USERS -> {
                g.draw(new Ellipse2D.Double(5, 4.5, 7, 7));
                g.draw(new Arc2D.Double(2, 14, 13, 13, 0, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(13.5, 4.5, 6, 7, -90, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(15, 14, 7, 13, 0, 90, Arc2D.OPEN));
            }
            case USER_PLUS -> {
                g.draw(new Ellipse2D.Double(5, 4, 8, 8));
                g.draw(new Arc2D.Double(1.5, 14.5, 15, 14, 0, 180, Arc2D.OPEN));
                g.draw(new Line2D.Double(19, 7, 19, 15));
                g.draw(new Line2D.Double(15, 11, 23, 11));
            }
            case CAMERA -> {
                Path2D p = new Path2D.Double();
                p.moveTo(3, 8); p.lineTo(7, 8); p.lineTo(9, 5); p.lineTo(15, 5); p.lineTo(17, 8);
                p.lineTo(21, 8); p.lineTo(21, 19); p.lineTo(3, 19); p.closePath();
                g.draw(p);
                g.draw(new Ellipse2D.Double(8.5, 9.5, 7, 7));
            }
            case EDIT -> {
                Path2D p = new Path2D.Double();
                p.moveTo(4, 20); p.lineTo(4.8, 15.6); p.lineTo(15.5, 4.9); p.lineTo(19.1, 8.5);
                p.lineTo(8.4, 19.2); p.closePath();
                g.draw(p);
                g.draw(new Line2D.Double(13, 7.4, 16.6, 11));
            }
            case INFO -> {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.draw(new Line2D.Double(12, 11, 12, 16.5));
                g.fill(new Ellipse2D.Double(10.8, 6.6, 2.4, 2.4));
            }
            case COMPASS -> {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                Path2D p = new Path2D.Double();
                p.moveTo(15.5, 8.5); p.lineTo(13.4, 13.4); p.lineTo(8.5, 15.5); p.lineTo(10.6, 10.6);
                p.closePath();
                g.fill(p);
                g.draw(p);
            }
            case CHECK -> {
                Path2D p = new Path2D.Double();
                p.moveTo(5, 12.5); p.lineTo(10, 17.5); p.lineTo(19, 7);
                g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(p);
            }
            case CROWN -> {
                Path2D p = new Path2D.Double();
                p.moveTo(3.5, 8); p.lineTo(8, 12); p.lineTo(12, 5); p.lineTo(16, 12); p.lineTo(20.5, 8);
                p.lineTo(18.5, 18); p.lineTo(5.5, 18); p.closePath();
                g.fill(p);
                g.draw(p);
            }
            case SMILE -> {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.fill(new Ellipse2D.Double(8, 8.5, 2.4, 2.8));
                g.fill(new Ellipse2D.Double(13.6, 8.5, 2.4, 2.8));
                g.draw(new Arc2D.Double(7.5, 9, 9, 8, 200, 140, Arc2D.OPEN));
            }
            case STICKER -> {
                Path2D p = new Path2D.Double();
                p.moveTo(7, 3); p.lineTo(17, 3);
                p.quadTo(21, 3, 21, 7); p.lineTo(21, 13); p.lineTo(13, 21); p.lineTo(7, 21);
                p.quadTo(3, 21, 3, 17); p.lineTo(3, 7); p.quadTo(3, 3, 7, 3); p.closePath();
                g.draw(p);
                Path2D f = new Path2D.Double();
                f.moveTo(21, 13); f.lineTo(16, 13); f.quadTo(13, 13, 13, 16); f.lineTo(13, 21);
                g.draw(f);
                g.fill(new Ellipse2D.Double(8, 8, 2.2, 2.6));
                g.fill(new Ellipse2D.Double(13.2, 8, 2.2, 2.6));
                g.draw(new Arc2D.Double(7.5, 9.5, 7, 5, 200, 120, Arc2D.OPEN));
            }
            case TRASH -> {
                g.draw(new Line2D.Double(4, 6.5, 20, 6.5));
                Path2D p = new Path2D.Double();
                p.moveTo(9, 6.5); p.lineTo(9, 3.5); p.lineTo(15, 3.5); p.lineTo(15, 6.5);
                g.draw(p);
                Path2D b = new Path2D.Double();
                b.moveTo(6, 6.5); b.lineTo(7, 20.5); b.lineTo(17, 20.5); b.lineTo(18, 6.5);
                g.draw(b);
            }
        }
        g.dispose();
    }
}
