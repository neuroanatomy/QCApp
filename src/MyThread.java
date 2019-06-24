import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MyThread extends Thread
{   
//    private boolean isStopped;
    
    public void run()
    {
        try
        {
            int i = QCApp.table.getSelectedRows()[0];
            List<String> subjects = new ArrayList<String>();
            subjects.add(QCApp.model.getValueAt(i, 2).toString());
            if (i + 1 < QCApp.model.getRowCount())
                subjects.add(QCApp.model.getValueAt(i + 1, 2).toString());
            if (i - 1 >= 0)
                subjects.add(QCApp.model.getValueAt(i - 1, 2).toString());
            if (i + 2 < QCApp.model.getRowCount())
                subjects.add(QCApp.model.getValueAt(i + 2, 2).toString());
            
            for (String subject : subjects) {
                File subjectDir = new File(QCApp.subjectsDir, subject);
                for (MyImage img : QCApp.images.imgList) {
                    sleep(1);
                    QCApp.images.volumes.getVolume(new File(subjectDir, img.volName), img.color);
                    sleep(1);
                    if (img.volback != null)
                        QCApp.images.volumes.getVolume(new File(subjectDir, img.volback), false);
                    sleep(1);
                    if (img.volbackall != null)
                        QCApp.images.volumes.getVolume(new File(subjectDir, img.volbackall), false);
                }
            }
        }
        catch(InterruptedException e)
        {
           System.out.println("my thread interrupted");
        }
    }
    
//    public stop() {
//        
//    }
}
