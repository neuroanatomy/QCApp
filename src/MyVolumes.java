import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class MyVolumes {
    public static int MAX_VOLUMES = 25;

    private List<MyVolume> volumes;

    public MyVolume getVolume(File file, boolean color) {
        synchronized (volumes) {
            for (MyVolume vol : volumes) {
                if (vol.file.equals(file) && vol.color == color) {
                    volumes.remove(vol);
                    volumes.add(vol);
                    return vol;
                }
            }
            QCApp.printStatusMessage("Loading volume \"" + file + "\"...");
            MyVolume newVol = new MyVolume(file, color);
            if (volumes.size() >= MAX_VOLUMES)
                volumes.subList(0, volumes.size() - MAX_VOLUMES).clear();
            volumes.add(newVol);
            return newVol;
        }
    }

    public MyVolumes() {
        volumes = Collections.synchronizedList(new ArrayList<MyVolume>());
    }
}
