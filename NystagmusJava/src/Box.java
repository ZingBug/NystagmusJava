import java.util.Objects;

/**
 * Created by ZingBug on 2017/10/11.
 */
public class Box implements Cloneable, Comparable<Box> {
    private double x;
    private double y;
    private double r;

    public Box() {
        this.x = 0f;
        this.y = 0f;
        this.r = 0f;
    }

    public Box(double x, double y, double r) {
        this.x = x;
        this.y = y;
        this.r = r;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setR(double r) {
        this.r = r;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getR() {
        return this.r;
    }

    public boolean equals(Object otherObject) {
        if (this == otherObject) return true;

        if (otherObject == null) return false;

        if (getClass() != otherObject.getClass()) return false;

        Box other = (Box) otherObject;

        return this.x == other.x && this.y == other.y && this.r == other.r;//必须对应着散列码相同
    }

    public int hashCode() {
        return Objects.hash(this.x, this.y, this.r);
    }

    public String toString() {
        return getClass().getName() + "[x=" + this.x + ",y=" + this.y + ",r=" + this.r + "]";
    }

    public Box clone() throws CloneNotSupportedException {
        return (Box) super.clone();
    }

    public int compareTo(Box other) {
        return Double.compare(this.r, other.getR());
    }
}
