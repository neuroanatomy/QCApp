import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.InputMismatchException;
import java.util.List;

import javax.swing.JComponent;

class MyGraphs extends JComponent {
    private static final long serialVersionUID = -6147931626622313147L;
    static int NB_REGIONS; // The number of brain regions
    static List<String> regions;

    public double[] mean, std, selectedSubjectVolumes;
    private File subjectsDir;

    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        float val;
        int i, x[][] = new int[NB_REGIONS][2];
        int j = 0, nRegions = 0;
        Dimension dim = this.getSize();
        Stroke dashed = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 5.0f, new float[] { 5.0f },
                0.0f);
    
        // count non empty regions
        for (i = 0; i < NB_REGIONS; i++)
            if (mean[i] != 0)
                nRegions++;

        for (i = 0; i < NB_REGIONS; i++)
            if (mean[i] != 0) {
                x[i][0] = (int) ((dim.width - 1) * j / (double) nRegions);
                x[i][1] = (int) ((dim.width - 1) * (j + 1) / (double) nRegions);
                j++;
            }

        // draw brain structure bars, with colours depending on selected-subject
        // values
        for (i = 0; i < NB_REGIONS; i++) {
            if (selectedSubjectVolumes[i] != 0) {
                val = (float) ((selectedSubjectVolumes[i] - mean[i]) / (2.0 * std[i]));
                if (val >= 0 && val <= 1)
                    g2.setColor(new Color(val, 1.0f - val, 0.0f));
                else if (val >= -1 && val < 0)
                    g2.setColor(new Color(0.0f, 1.0f + val, -val));
                else
                    g2.setColor(Color.white);
            }
            else
                g2.setColor(Color.white);
            g2.fillRect(x[i][0], 0, x[i][1], dim.height);
            g2.setColor(Color.black);
            g2.drawRect(x[i][0], 0, x[i][1], dim.height);
        }

        // draw dots for selected subject values
        g2.setColor(Color.black);
        for (i = 0; i < NB_REGIONS; i++)
            if (selectedSubjectVolumes[i] != 0) {
                val = (float) (0.5f + (selectedSubjectVolumes[i] - mean[i]) / (2.0 * std[i]) / 2.0);
                if (val < 0)
                    val = 0;
                if (val > 1)
                    val = 1;
                g2.fillOval((x[i][0] + x[i][1]) / 2 - 5, (int) (dim.height * (1 - val)) - 5, 11, 11);
            }

        // draw mean and +/- 1 std values
        g2.setColor(Color.black);
        g2.drawLine(0, dim.height / 2, dim.width, dim.height / 2);
        g2.setStroke(dashed);
        g2.drawLine(0, dim.height / 4, dim.width, dim.height / 4);
        g2.drawLine(0, dim.height * 3 / 4, dim.width, dim.height * 3 / 4);
    
        // to cope with a MAC OS X bug
        FontRenderContext frc = new FontRenderContext(g2.getTransform(), true, true);

        // draw brain structure names
        for (i = 0; i < NB_REGIONS; i++)
            if (mean[i] != 0) {
                g2.rotate(Math.PI / 2.0);
                g2.drawGlyphVector(g2.getFont().createGlyphVector(frc, regions.get(i)), 5, -(x[i][0] + x[i][1] - g2.getFont().getSize() / 2) / 2);
                g2.rotate(-Math.PI / 2.0);
            }
    }

    public int getVolumesForSubject(String subject, double x[]) {
        BufferedReader input;
        int err = 0;
        for (int i = 0; i < x.length; i++)
            x[i] = 0;
    
        try {
            input = new BufferedReader(new FileReader(subjectsDir + "/" + subject + "/" + QCApp.statsFileName));
            String line;
            String[] parts;
            while ((line = input.readLine()) != null) {
                if (line.startsWith("#")) {
                    // Load ICV and BrainSeg data
                    if (line.startsWith("# Measure")) {
                        parts = line.substring(10).trim().split(", ");
                        for (String measure : QCApp.measures) {
                            if ((parts[0].equals(measure) || parts[1].equals(measure)) && parts[4].equals("mm^3")) {
                                int i = regions.indexOf(measure);
                                x[i] = Float.valueOf(parts[3]);
                                break;
                            }
                        }
                    }
                }
                // Segmented Data
                else {
                    parts = line.trim().split(" +");
                    if (parts.length >= 5) {
                        int i = regions.indexOf(parts[4]);
                        if (i > -1)
                            x[i] = Float.valueOf(parts[3]);
                    }
                }
            }
            input.close();
        } catch (IOException e) {
            err = 1;
            return err;
        } catch (InputMismatchException e) {
            err = 1;
            return err;
        }
        return err;
    }

    public void configure(File subjectsDir, List<String> subjects) {

        NB_REGIONS = QCApp.colorLUT.size() + QCApp.measures.length;
        regions = new ArrayList<String>(Arrays.asList(QCApp.measures));
        //regions.addAll(Arrays.asList(QCApp.measures));
        for (RegionColor regionColor : QCApp.colorLUT) {
            regions.add(regionColor.label);
        }
        mean = new double[NB_REGIONS];
        std = new double[NB_REGIONS];
        selectedSubjectVolumes = new double[NB_REGIONS];
    
    
        double s0 = 0;
        double s1[] = new double[NB_REGIONS];
        double s2[] = new double[NB_REGIONS];
        double x[] = new double[NB_REGIONS];
        int i;
        int j;
        int err;

        this.subjectsDir = subjectsDir;

        for (i=0; i<subjects.size(); i++) {
            QCApp.printStatusMessage("Configuring stat graphs... " + (i + 1) + "/" + subjects.size());
            err = getVolumesForSubject(subjects.get(i), x);
            if (err == 1) {
                QCApp.setQC(subjects.get(i), "Segmentation results unavailable");
                continue;
            }

            for (j = 0; j < NB_REGIONS; j++) {
                s1[j] += x[j];
                s2[j] += x[j] * x[j];
            }
            s0++;
        }
        for (j = 0; j < NB_REGIONS; j++) {
            mean[j] = s1[j] / s0;
            std[j] = Math.sqrt((s0 * s2[j] - s1[j] * s1[j]) / (s0 * (s0 - 1)));
            System.out.println(regions.get(j) + ":\t" + mean[j] + " +- " + std[j]);
        }
        repaint();
    }

    public void setSelectedSubject(String subject) {
        getVolumesForSubject(subject, selectedSubjectVolumes);
        repaint();
    }
}