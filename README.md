# QCApp
Fast visual quality control tool for segmented structures volumetry from FreeSurfer, coded in Java.

The application relies on Apache Commons libraries which can be easily downloaded with Apache Maven. The interface looks like this:

![Screenshot](/images/QCApp.png)

How to use it:

1. First choose the directory with your subject data and select FreeSurfer in the Combo Box.
2. The application puts the list of subjects in the Table to the left. Internally, it also reads the text files with the segmentation results and computes the sample mean and std for each structure.
3. When you click on a subject in the list, it loads cortical segmentation, the skull-stripped volume, and the original volume. If there's no "qc" directory inside the subject's directory, QCApp will create one and save sagittal, coronal and axial slice PNG images for the 3 types of volume. If the "qc" directory is already present, QCApp will just display the PNGs (which is faster than having to make them from the .nii.gz volumes). Once the PNG images are all computed, browsing through the subjects it's very quick using the table, as you don't have open each of the subjects data volume one by one from the File menu. This should help people to QC their data rapidly.
4. The slices will be displayed on the black region to the right. If you click on one of those images, QCApp will open the associated 3D data volume. You can browse through the slices by dragging the mouse pointer from top to bottom. There's a BACK button (top-right) to go back to the image with all the slices. ![Screenshot](/images/QCApp-slice.png)

5. To simplify the detection of errors, the small area on the bottom-left corner shows the volumes of the subject compared with the whole sample values. The black dots represent the subject values. The mid-line is the mean, the dashed line is 1 STD, and the top and bottom limits are 2 STD. The colour of the bar is green if the value is close to the mean, goes to red if it's larger, and to blue if it's smaller. If the volume of a structure in the selected subject is larger or smaller than 2 STD, the black dot is not drawn and the colour bar is filled with white, which should help detecting outliers/errors.
6. If a subject does not have the segmentation text files (which most often indicates that something went wrong during the processing), the QC value in the table will be set to 0 (= Data unavailable), and a comment will be added indicating that something went wrong. I could also change the QC value to 2 (=Doubts) if the value of any region is larger or smaller than 2 STDs. People should then check if there's something wrong with the data, and either move the value to QC=1 (=OK), or eventually to 3 (=Exclude). The "Save QC" button will save the data in the table to a text file (tab-separated fields).
7. Finally, to avoid having to wait QCApp to compute the PNG images the first time a subject is selected, the application can also run without GUI. In that case, QCApp takes the subject directory as only argument, draws the PNGs, and saves them in the 'qc' directory. This could be eventually done as part of the analysis pipeline...

To compile the code, go to QCApp directory and call:

    mvn package

Run it like this (with GUI):

    java -jar target/QCApp-0.0.1-SNAPSHOT.jar

or like this (without GUI) (for FreeSurfer):

    java -Djava.awt.headless=true -jar target/QCApp-0.0.1-SNAPSHOT.jar target/config/FreeSurfer.xml /path/to/subject/directory

The volumes QCApp looks for are specified in FreeSurfer.xml:
    brain.mgz
    aseg.mgz
    orig.mgz

Additionally, you can also customize the volumes and the color map displayed by QCApp by creating a new xml configuration file in the config directory with the same architecture as FreeSurfer.xml. QCApp reads volumes in .mgz or .nii.gz format, color maps in FreeSurfer color LUT format, and segmentation data in FreeSurfer .stats format.