package ohkt.physics;

/** Resultado de raycast contra o mundo estatico. */
public final class RaycastHit {
    public boolean hit;
    public float t;
    public float px, py, pz;
    public float nx, ny, nz;

    public void set(boolean hit, float t, float px, float py, float pz, float nx, float ny, float nz) {
        this.hit = hit; this.t = t;
        this.px = px; this.py = py; this.pz = pz;
        this.nx = nx; this.ny = ny; this.nz = nz;
    }
}
