class MyImage {
    public String volName;
    public boolean color;
    public String effect;
    public String output;
    public String volback;
    public String volbackall;
    private int plane;

    public MyImage(String volName, boolean color, String effect, String output, String volback, String volbackall,
            int plane) {
        this.volName = volName;
        this.color = color;
        this.effect = effect;
        this.output = output;
        this.volback = volback;
        this.volbackall = volbackall;
        this.plane = plane;
    }

    public int getPlane() {
        return plane;
    }

    public void setPlane(int plane) {
        this.plane = plane;
    }

    public String getPlaneName() {
        String volPlane;
        if (plane == 0)
            volPlane = "X";
        else if (plane == 1)
            volPlane = "Y";
        else if (plane == 2)
            volPlane = "Z";
        else
            volPlane = "";
        return volPlane;
    }

    public int setPlaneName(String volPlane) {
        int err = 0;
        if (volPlane.equals("X"))
            plane = 0;
        else if (volPlane.equals("Y"))
            plane = 1;
        else if (volPlane.equals("Z"))
            plane = 2;
        else
            err = 1;
        return err;
    }

    public String getImageFileName() {
        return output + "." + getPlaneName() + ".png";
    }
}