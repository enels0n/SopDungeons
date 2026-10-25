package net.enelson.sopdungeons.model;

import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RelativePointTest {
    @Test
    public void rotatesAroundYAxis() {
        RelativePoint point = new RelativePoint(3, 2, 5);
        RelativePoint rotated = point.rotate(90);
        assertEquals(-5, rotated.getX());
        assertEquals(2, rotated.getY());
        assertEquals(3, rotated.getZ());
        assertEquals(-3, point.rotate(180).getX());
        assertEquals(-5, point.rotate(180).getZ());
    }

    @Test
    public void usesTheSameRotationDirectionAsWorldEditPaste() {
        RelativePoint point = new RelativePoint(3, 2, 5);
        RelativePoint rotated = point.rotate(90);
        Vector3 worldEdit = new AffineTransform().rotateY(-90).apply(Vector3.at(3, 2, 5));
        assertEquals(rotated.getX(), (int) Math.round(worldEdit.getX()));
        assertEquals(rotated.getY(), (int) Math.round(worldEdit.getY()));
        assertEquals(rotated.getZ(), (int) Math.round(worldEdit.getZ()));
    }
}
