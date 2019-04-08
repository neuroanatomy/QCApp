import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.JComponent;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;

class MyImages extends JComponent {

	private class Voxel {
		
		int x;
		int y;
		int z;
		int value;

	}
	
	private static final long serialVersionUID = -1848304834958653184L;
	private Rectangle rect[];
	private Boolean toggle;
	private int selectedImage = 0;
	public boolean initialized = false;
	private int[] selectedSlice = new int[3];
	private double yprev;
	private int prevSlice;
	private int prevPlane;
	private double prevHeight;
	private double scale;
	private File subjectDir;
	MyImage[] imgList;
	int margin = 5; // margin for images in pixels
	private BufferedImage bufImg0; // single view bitmap image
	private BufferedImage bufImgList[]; // bitmap images
	static int cmap[][]; // colour maps
	static String cname[];
	private int refImage;
	private int[][] refBounds = new int[3][2];
	
	private final static float[][] X = {	// X-plane transformation matrix
			{ 0, 1, 0 },
			{ 0, 0, -1 },
			{ 1, 0, 0 }
	};
	private final static float[][] Y = {	// Y-plane transformation matrix
			{ 1, 0, 0 },
			{ 0, 0, -1 },
			{ 0, 1, 0 }
	};
	private final static float[][] Z = {	// Z-plane transformation matrix
			{ 1, 0, 0 },
			{ 0, -1, 0 },
			{ 0, 0, 1 }
	};
	
	MyVolumes volumes;

    public MyImages(File subjectDir) {
        this();
        renew();
        QCApp.usePictures = true;
        changeSubjectDir(subjectDir);
    }

    public MyImages() {
        this.setPreferredSize(new Dimension(800, 512));
        this.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                mouseDownOnImage(e);
            }
        });
        this.addMouseMotionListener(new MouseAdapter() {
            public void mouseDragged(MouseEvent e) {
                mouseDraggedOnImage(e);
            }
        });
        this.addMouseMotionListener(new MouseAdapter() {
            public void mouseMoved(MouseEvent e) {
                mouseMovedOnImage(e);
            }
        });
        this.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                mouseMovedOnImage(e);
            }
        });
        this.addMouseWheelListener(new MouseWheelListener() {
            public void mouseWheelMoved(MouseWheelEvent e) {
                mouseWheelRotatedOnImage(e);
            }
        });

        volumes = new MyVolumes();
    }

    public void renew() {
        cmap = new int[2][256];
        cname = new String[256];

        // init greyscale colourmap
        for (int i = 0; i < cmap[0].length; i++)
            cmap[0][i] = rgb2value(i, i, i);

        // init segmentation label colourmap
        for (RegionColor regionColor : QCApp.colorLUT) {
            int No = regionColor.No;
            if (No >= 0 && No <= 255) {
                cmap[1][No] = rgb2value(regionColor.R, regionColor.G, regionColor.B);
                cname[No] = regionColor.label;
            }
		}

		// init image list
		imgList = new MyImage[QCApp.imageConfigs.size() * 3];
		int j = 0;
		refImage = 0;
		for (HierarchicalConfiguration<ImmutableNode> config : QCApp.imageConfigs) {
			String volume = config.getString("volume");
			boolean color = config.getBoolean("color");
			String effect = config.getString("effect");
			String output = config.getString("output");
			String volback = config.getString("volback");
			String volbackall = config.getString("volbackall");
			if (color && refImage == 0)
				refImage = j;
			for (int plane = 0; plane < 3; plane++) {
				imgList[j] = new MyImage(volume, color, effect, output, volback, volbackall, plane);
				j++;
			}
		}
		
		selectedImage = 0;
		initialized = false;
		volumes = new MyVolumes();
		rect = Stream.generate(() -> new Rectangle())
			    .limit(imgList.length)
			    .toArray(Rectangle[]::new);
		repaint();
	}

	public void changeSubjectDir(File subjectDir) {
		float tmp[] = new float[3], tmpd[] = new float[3];
		int dim1[] = new int[3];
		int[][] bounds = new int[3][2];
		//float[][] invS = new float[3][3];
		
		this.subjectDir = subjectDir;
		MyVolume vol = volumes.getVolume(new File(subjectDir, imgList[refImage].volName), imgList[refImage].color);
		
		// Set selected slices at the center of the segmentation
		
		// find dimension of volume
		tmp[0] = vol.dim[0] - 1;
		tmp[1] = vol.dim[1] - 1;
		tmp[2] = vol.dim[2] - 1;
		multMatVec(tmpd, vol.R, tmp);
		dim1[0] = Math.round(tmpd[0]);
		dim1[1] = Math.round(tmpd[1]);
		dim1[2] = Math.round(tmpd[2]);

		// find bounding box
		tmp[0] = vol.boundingBox[1][0];
		tmp[1] = vol.boundingBox[1][2];
		tmp[2] = vol.boundingBox[1][4];
		multMatVec(tmpd, vol.R, tmp);
		bounds[0][0] = Math.round(tmpd[0]);
		bounds[1][0] = Math.round(tmpd[1]);
		bounds[2][0] = Math.round(tmpd[2]);
		tmp[0] = vol.boundingBox[1][1];
		tmp[1] = vol.boundingBox[1][3];
		tmp[2] = vol.boundingBox[1][5];
		multMatVec(tmpd, vol.R, tmp);
		bounds[0][1] = Math.round(tmpd[0]);
		bounds[1][1] = Math.round(tmpd[1]);
		bounds[2][1] = Math.round(tmpd[2]);
		
		selectedSlice[0] = (bounds[0][0] + bounds[0][1]) / 2;
		selectedSlice[1] = (bounds[1][0] + bounds[1][1]) / 2;
		selectedSlice[2] = (bounds[2][0] + bounds[2][1]) / 2;

		refBounds[0][0] = Math.min(bounds[0][0], bounds[0][1]);
		refBounds[0][1] = Math.max(bounds[0][0], bounds[0][1]);
		refBounds[1][0] = Math.min(bounds[1][0], bounds[1][1]);
		refBounds[1][1] = Math.max(bounds[1][0], bounds[1][1]);
		refBounds[2][0] = Math.min(bounds[2][0], bounds[2][1]);
		refBounds[2][1] = Math.max(bounds[2][0], bounds[2][1]);
		
		// To use image files, call setImages(true)
		setImages(QCApp.usePictures);
	}
	
	private void mouseDownOnImage(MouseEvent e) {
		int i;

		if (!initialized)
			return;

		if (selectedImage == 0) {
			// Open selected image
			for (i = 0; i < rect.length; i++) {
				if (rect[i].contains(e.getPoint())) {
					selectedImage = i + 1;
					toggle = true;
					break;
				}
			}
			setImages();
		} else {
			// Check for button click
			Dimension d = getParent().getSize();
			Rectangle backRect = new Rectangle(d.width - 10 - 48, 10, 48, 20);
			if (backRect.contains(e.getPoint())) {
				// back button is clicked
				selectedImage = 0;
				setImages();
			}
			Rectangle toggleRect = new Rectangle(10, 10, 60, 20);
			if (toggleRect.contains(e.getPoint())) {
				// toggle button is clicked
				toggle = !toggle;
				setImages();
				repaint();
			}
		}
	}

	private void mouseDraggedOnImage(MouseEvent e) {
		if (prevHeight != 0) {
			int i = prevPlane;
			int b0 = refBounds[i][0];
			int b1 = refBounds[i][1];

			selectedSlice[i] = prevSlice + (int) (((e.getY() - yprev) / prevHeight) * (b1 - b0));
			if (selectedSlice[i] < b0)
				selectedSlice[i] = b0;
			if (selectedSlice[i] > b1)
				selectedSlice[i] = b1;
			setImages();
			repaint();
		}
	}
	
	int counter = 0;

	private void mouseMovedOnImage(MouseEvent e) {
		int i;
		yprev = e.getY();
		prevHeight = 0;

		if (!initialized)
			return;

		for (i = 0; i < rect.length; i++) {
			if (rect[i].contains(e.getPoint())) {
				prevHeight = rect[i].getHeight();
				prevPlane = imgList[i].getPlane();
				int x = (int)Math.round((e.getX() - rect[i].x) / scale - margin);
				int y = (int)Math.round((e.getY() - rect[i].y) / scale - margin);
				MyVolume vol = volumes.getVolume(new File(subjectDir, imgList[i].volName), imgList[i].color);
				MyVolume volRef;
				String volBack;
				if (selectedImage == 0) {
					volBack = imgList[i].volbackall;
				} else {
					volBack = imgList[i].volback;
				}
				if (volBack != null) {
					volRef = volumes.getVolume(new File(subjectDir, volBack), false);
				} else {
					volRef = vol;
				}
				int [][] boundingBox = volRef.boundingBox;
				try {
					Voxel voxel = getVoxel(vol, selectedSlice, imgList[i].getPlane(), boundingBox, x, y);
					if (imgList[i].color) {
						String region = cname[voxel.value];
						QCApp.printStatusMessage(String.format("R:%d A:%d S:%d %s", voxel.x, voxel.y, voxel.z, region));
					} else {
						QCApp.printStatusMessage(String.format("R:%d A:%d S:%d %d", voxel.x, voxel.y, voxel.z, voxel.value));
					}
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				break;
			}
		}
		
		// Cursor is outside of rectangles
		if (i == rect.length) {
			QCApp.printStatusMessage("");
		}

		prevSlice = selectedSlice[prevPlane];
	}
	

	private void mouseWheelRotatedOnImage(MouseWheelEvent e) {
		int i;
	    int steps = -e.getWheelRotation();

		if (!initialized) {
			e.getComponent().getParent().dispatchEvent(e);
			return;
		}
	    
		for (i = 0; i < rect.length; i++) {
			if (rect[i].contains(e.getPoint()))
				break;
		}
		
		// Cursor is outside of rectangles
		if (i == rect.length) {
			e.getComponent().getParent().dispatchEvent(e);
			return;
		}

		selectedSlice[prevPlane] = prevSlice + steps;
		if (selectedSlice[prevPlane] < refBounds[prevPlane][0])
			selectedSlice[prevPlane] = refBounds[prevPlane][0];
		if (selectedSlice[prevPlane] > refBounds[prevPlane][1])
			selectedSlice[prevPlane] = refBounds[prevPlane][1];
		setImages();

		prevSlice = selectedSlice[prevPlane];
}

	public static void multMatVec(float[] rV, float[][] M, float[] V) {
		rV[0] = M[0][0] * V[0] + M[0][1] * V[1] + M[0][2] * V[2];
		rV[1] = M[1][0] * V[0] + M[1][1] * V[1] + M[1][2] * V[2];
		rV[2] = M[2][0] * V[0] + M[2][1] * V[1] + M[2][2] * V[2];
	}

	public static void multMat(float[][] P, float[][] M, float[][] N) {
		P[0][0] = M[0][0] * N[0][0] + M[0][1] * N[1][0] + M[0][2] * N[2][0];
		P[0][1] = M[0][0] * N[0][1] + M[0][1] * N[1][1] + M[0][2] * N[2][1];
		P[0][2] = M[0][0] * N[0][2] + M[0][1] * N[1][2] + M[0][2] * N[2][2];
		P[1][0] = M[1][0] * N[0][0] + M[1][1] * N[1][0] + M[1][2] * N[2][0];
		P[1][1] = M[1][0] * N[0][1] + M[1][1] * N[1][1] + M[1][2] * N[2][1];
		P[1][2] = M[1][0] * N[0][2] + M[1][1] * N[1][2] + M[1][2] * N[2][2];
		P[2][0] = M[2][0] * N[0][0] + M[2][1] * N[1][0] + M[2][2] * N[2][0];
		P[2][1] = M[2][0] * N[0][1] + M[2][1] * N[1][1] + M[2][2] * N[2][1];
		P[2][2] = M[2][0] * N[0][2] + M[2][1] * N[1][2] + M[2][2] * N[2][2];
	}

	public static float detMat(float[][] M) {
		return M[0][0] * M[1][1] * M[2][2] + M[1][0] * M[2][1] * M[0][2] + M[2][0] * M[0][1] * M[1][2]
				- M[2][0] * M[1][1] * M[0][2] - M[0][0] * M[2][1] * M[1][2] - M[1][0] * M[0][1] * M[2][2];
	}

	public static void invMat(float[][] rM, float[][] M) {
		float d = detMat(M);
		
		rM[0][0] = (M[1][1] * M[2][2] - M[2][1] * M[1][2]) / d;
		rM[0][1] = (M[2][1] * M[0][2] - M[0][1] * M[2][2]) / d;
		rM[0][2] = (M[0][1] * M[1][2] - M[1][1] * M[0][2]) / d;
		rM[1][0] = (M[2][0] * M[1][2] - M[1][0] * M[2][2]) / d;
		rM[1][1] = (M[0][0] * M[2][2] - M[2][0] * M[0][2]) / d;
		rM[1][2] = (M[1][0] * M[0][2] - M[0][0] * M[1][2]) / d;
		rM[2][0] = (M[1][0] * M[2][1] - M[2][0] * M[1][1]) / d;
		rM[2][1] = (M[2][0] * M[0][1] - M[0][0] * M[2][1]) / d;
		rM[2][2] = (M[0][0] * M[1][1] - M[1][0] * M[0][1]) / d;
}
	
	static int value2rgb(int v, int cmapindex) {
		int rgb = 0;
		
		if (v < 0)
			return 0;
		if (v > 255) {
			if (cmapindex == 0)
				v = 255;
			else
				v = 0;
		}
		rgb = cmap[cmapindex][v];
		
		return rgb;
	}
	
	static int rgb2value(int r, int g, int b) {
		return r << 16 | g << 8 | b;
	}
	
	private Voxel getVoxel(MyVolume vol, int[] selectedSlice, int plane, int[][] boundingBox, int x, int y) throws Exception
	{
		int z;
		int x1, y1, z1;
		int x2, y2, z2;
		float[][] invR = new float[3][3];	// inverse of the coordinate systems matrix
		float[][] P, invP = new float[3][3];	// view plane transformation matrix and its inverse
		float[][] T = new float[3][3], invT = new float[3][3]; // combined transformation matrix and its inverse
		float tmp[] = new float[3], tmpd[] = new float[3], tmpx[] = new float[3];
		Rectangle rect = new Rectangle(0, 0, 1, 1);
		int[][] bounds = new int[2][2];
		// transform volume to view plane
		switch (plane) {
		case 0:
			P = X;
			break;
		case 1:
			P = Y;
			break;
		case 2:
			P = Z;
			break;
		default:
			throw new Exception("No plane selected");
		}
		
		multMat(T, P, vol.R);
		invMat(invT, T);
		invMat(invR, vol.R);
		invMat(invP, P);

		// find bounding box
		tmp[0] = boundingBox[0][0];
		tmp[1] = boundingBox[0][2];
		tmp[2] = boundingBox[0][4];
		multMatVec(tmpd, T, tmp);
		bounds[0][0] = Math.round(tmpd[0]);
		bounds[1][0] = Math.round(tmpd[1]);
		tmp[0] = boundingBox[0][1];
		tmp[1] = boundingBox[0][3];
		tmp[2] = boundingBox[0][5];
		multMatVec(tmpd, T, tmp);
		bounds[0][1] = Math.round(tmpd[0]);
		bounds[1][1] = Math.round(tmpd[1]);

		rect.x = Math.min(bounds[0][0], bounds[0][1]);
		rect.y = Math.min(bounds[1][0], bounds[1][1]);
		rect.width = Math.max(bounds[0][0], bounds[0][1]) - rect.x + 1;
		rect.height = Math.max(bounds[1][0], bounds[1][1]) - rect.y + 1;
		
		// find selected slice corresponding to z
		tmp[0] = (float) selectedSlice[0];
		tmp[1] = (float) selectedSlice[1];
		tmp[2] = (float) selectedSlice[2];
		multMatVec(tmpd, P, tmp);
		z = Math.round(tmpd[2]);
		
		Voxel voxel = new Voxel();

		tmp[0] = x + rect.x;
		tmp[1] = y + rect.y;
		tmp[2] = z;
		multMatVec(tmpx, invP, tmp);
		x1 = Math.round(tmpx[0]);
		y1 = Math.round(tmpx[1]);
		z1 = Math.round(tmpx[2]);
		multMatVec(tmp, invR, tmpx);
		x2 = Math.round(tmp[0]);
		y2 = Math.round(tmp[1]);
		z2 = Math.round(tmp[2]);

		voxel.x = x1;
		voxel.y = y1;
		voxel.z = z1;
		voxel.value = vol.getValue(x2, y2, z2);
		
		return voxel;
	}

	private BufferedImage drawSlice(MyVolume vol, int[] selectedSlice, int plane, int cmapindex) throws Exception
	// draw slice with position 't' in the plane 'plane' at position ox, oy
	// using colourmap 'cmapindex'
	{
		return drawSlice(vol, null, selectedSlice, plane, cmapindex,
				true, 1);
	}

	private BufferedImage drawSlice(MyVolume vol, MyVolume volBack, int[] selectedSlice, int plane, int cmapindex,
			Boolean toggle, float opacity) throws Exception
	// draw slice with position 't' in the plane 'plane' at position ox, oy
	// using colourmap 'cmapindex'
	{
		BufferedImage theImg;
		int x, y, z;
		int x1, y1, z1;
		int rgb, rgb0 = 0;
		int v, v0;
//		int sliceMax = 0; // maximum slice value
//		float slice;
		float[][] P; //, invP = new float[3][3];	// view plane transformation matrix and its inverse
		float[][] T = new float[3][3], invT = new float[3][3]; // combined transformation matrix and its inverse
		float tmp[] = new float[3], tmpd[] = new float[3], tmpx[] = new float[3];
		int dim1[] = new int[3];
		float pixdim1[] = new float[3];
		Rectangle rect = new Rectangle(0, 0, 1, 1);
		int[][] bounds = new int[2][2];
		MyVolume volRef;

		// transform volume to view plane
		switch (plane) {
		case 0:
			P = X;
			break;
		case 1:
			P = Y;
			break;
		case 2:
			P = Z;
			break;
		default:
			throw new Exception("No plane selected");
		}
		
		multMat(T, P, vol.R);
		invMat(invT, T);

		if (volBack != null) {
			volRef = volBack;
		} else {
			volRef = vol;
		}
		
		tmp[0] = volRef.dim[0] - 1;
		tmp[1] = volRef.dim[1] - 1;
		tmp[2] = volRef.dim[2] - 1;
		multMatVec(tmpd, T, tmp);
		dim1[0] = Math.round(tmpd[0]);
		dim1[1] = Math.round(tmpd[1]);
		dim1[2] = Math.round(tmpd[2]);

        tmp[0] = vol.pixdim[0];
        tmp[1] = vol.pixdim[1];
        tmp[2] = vol.pixdim[2];
        multMatVec(tmpd, T, tmp);
        pixdim1[0] = Math.abs(tmpd[0]);
        pixdim1[1] = Math.abs(tmpd[1]);
        pixdim1[2] = Math.abs(tmpd[2]);

		// find bounding box
		tmp[0] = volRef.boundingBox[0][0];
		tmp[1] = volRef.boundingBox[0][2];
		tmp[2] = volRef.boundingBox[0][4];
		multMatVec(tmpd, T, tmp);
		bounds[0][0] = Math.round(tmpd[0]);
		bounds[1][0] = Math.round(tmpd[1]);
		tmp[0] = volRef.boundingBox[0][1];
		tmp[1] = volRef.boundingBox[0][3];
		tmp[2] = volRef.boundingBox[0][5];
		multMatVec(tmpd, T, tmp);
		bounds[0][1] = Math.round(tmpd[0]);
		bounds[1][1] = Math.round(tmpd[1]);

		rect.x = Math.min(bounds[0][0], bounds[0][1]);
		rect.y = Math.min(bounds[1][0], bounds[1][1]);
		rect.width = Math.max(bounds[0][0], bounds[0][1]) - rect.x + 1;
		rect.height = Math.max(bounds[1][0], bounds[1][1]) - rect.y + 1;
		
		// find selected slice corresponding to z
		tmp[0] = (float) selectedSlice[0];
		tmp[1] = (float) selectedSlice[1];
		tmp[2] = (float) selectedSlice[2];
		multMatVec(tmpd, P, tmp);
//		slice = Math.abs(tmpd[2]);
//		z = Math.round(slice * Math.abs(dim1[2])) + Math.min(dim1[2], 0);
		z = Math.round(tmpd[2]);

//		// find maximum brightness
//		for (x = rect.x; x < rect.width + rect.x; x++)
//			for (y = rect.y; y < rect.height + rect.y; y++) {
//				tmp[0] = x;
//				tmp[1] = y;
//				tmp[2] = z;
//				multMatVec(tmpx, invT, tmp);
//				x1 = Math.round(tmpx[0]);
//				y1 = Math.round(tmpx[1]);
//				z1 = Math.round(tmpx[2]);
//
//				v = Math.round(volBack.getValue(x1, y1, z1));
//				if (v > sliceMax)
//					sliceMax = v;
//			}

        // draw slice
        theImg = new BufferedImage(rect.width, rect.height, BufferedImage.TYPE_INT_RGB);
        for (x = 0; x < rect.width; x++)
            for (y = 0; y < rect.height; y++) {
                tmp[0] = x + rect.x;
                tmp[1] = y + rect.y;
                tmp[2] = z;
                multMatVec(tmpx, invT, tmp);
                x1 = Math.round(tmpx[0]);
                y1 = Math.round(tmpx[1]);
                z1 = Math.round(tmpx[2]);

				v = vol.getValue(x1, y1, z1);
				if (cmapindex == 0)
					rgb = value2rgb(v, 0);
				else
					rgb = value2rgb(v, cmapindex);
				if (volBack != null) {
					v0 = volBack.getValue(x1, y1, z1);
					rgb0 = value2rgb(v0, 0);
				}
				if (rgb > 0 && toggle) {
					int r = (rgb >> 16) & 0xFF;
					int g = (rgb >> 8) & 0xFF;
					int b = rgb & 0xFF;
					int r0 = (rgb0 >> 16) & 0xFF;
					int g0 = (rgb0 >> 8) & 0xFF;
					int b0 = rgb0 & 0xFF;
					int r1 = Math.round(r * opacity + r0 * (1-opacity));
					int g1 = Math.round(g * opacity + g0 * (1-opacity));
					int b1 = Math.round(b * opacity + b0 * (1-opacity));
					int rgb1 = r1 << 16 | g1 << 8 | b1;
					theImg.setRGB(x, y, rgb1);
				}
				else
					theImg.setRGB(x, y, rgb0);
			}
		
		// scale
		BufferedImage scaledImg = new BufferedImage(Math.round(rect.width * pixdim1[0] + margin * 2),
				Math.round(rect.height * pixdim1[1] + margin * 2), BufferedImage.TYPE_INT_RGB);
		AffineTransform at = new AffineTransform();
		at.scale(pixdim1[0], pixdim1[1]);
		at.translate(margin, margin);
		AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
		return scaleOp.filter(theImg, scaledImg);
	}

    private BufferedImage drawVolume(MyVolume vol, int plane, int cmapindex) throws Exception {
        BufferedImage theImg;
        int x, y, x1, y1, z1, rgb;
        float z, s0, s1;
        int v;
        float[][] P;//, invP = new float[3][3];
        float[][] T = new float[3][3], invT = new float[3][3];
        float[] tmp = new float[3], tmpd = new float[3], tmpx = new float[3];
        float[] pixdim1 = new float[3];
        int r, g, b;
        Rectangle rect = new Rectangle(0, 0, 0, 0);
        int[][] bounds = new int[3][2];

        // transform volume to view plane
        switch (plane) {
        case 0:
            P = X;
            break;
        case 1:
            P = Y;
            break;
        case 2:
            P = Z;
            break;
        default:
            throw new Exception("No plane selected");
        }

        multMat(T, P, vol.R);
        invMat(invT, T);
        // multMat(invT, vol.S, invP);
        // invMat(T, invT);

        // find dimension of pixels
        tmp[0] = vol.pixdim[0];
        tmp[1] = vol.pixdim[1];
        tmp[2] = vol.pixdim[2];
        multMatVec(tmpd, T, tmp);
        pixdim1[0] = Math.abs(tmpd[0]);
        pixdim1[1] = Math.abs(tmpd[1]);
        pixdim1[2] = Math.abs(tmpd[2]);

        // find 1st and last non-empty slices (for lighting) and bounding box
        tmp[0] = vol.boundingBox[cmapindex][0];
        tmp[1] = vol.boundingBox[cmapindex][2];
        tmp[2] = vol.boundingBox[cmapindex][4];
        multMatVec(tmpd, T, tmp);
        bounds[0][0] = Math.round(tmpd[0]);
        bounds[1][0] = Math.round(tmpd[1]);
        bounds[2][0] = Math.round(tmpd[2]);
        tmp[0] = vol.boundingBox[cmapindex][1];
        tmp[1] = vol.boundingBox[cmapindex][3];
        tmp[2] = vol.boundingBox[cmapindex][5];
        multMatVec(tmpd, T, tmp);
        bounds[0][1] = Math.round(tmpd[0]);
        bounds[1][1] = Math.round(tmpd[1]);
        bounds[2][1] = Math.round(tmpd[2]);

        rect.x = Math.min(bounds[0][0], bounds[0][1]);
        rect.y = Math.min(bounds[1][0], bounds[1][1]);
        rect.width = Math.max(bounds[0][0], bounds[0][1]) - rect.x + 1;
        rect.height = Math.max(bounds[1][0], bounds[1][1]) - rect.y + 1;

        s0 = Math.min(bounds[2][0], bounds[2][1]); // 1st
        s1 = Math.max(bounds[2][0], bounds[2][1]); // last

		// draw volume
		theImg = new BufferedImage(rect.width, rect.height, BufferedImage.TYPE_INT_RGB);
		for (z = s0; z <= s1; z++)
			for (x = 0; x < rect.width; x++)
				for (y = 0; y < rect.height; y++) {
					tmp[0] = x + rect.x;
					tmp[1] = y + rect.y;
					tmp[2] = z;
					multMatVec(tmpx, invT, tmp);
					x1 = Math.round(tmpx[0]);
					y1 = Math.round(tmpx[1]);
					z1 = Math.round(tmpx[2]);

					v = vol.getValue(x1, y1, z1);
					if (cmapindex == 0)
						rgb = value2rgb(v, 0);
					else
						rgb = value2rgb(v, cmapindex);
					if (rgb == 0)
						continue;

					// light
					r = (int) ((rgb >> 16) * Math.pow((z - s0) / (s1 - s0), 0.5));
					g = (int) (((rgb & 0xff00) >> 8) * Math.pow((z - s0) / (s1 - s0), 0.5));
					b = (int) ((rgb & 0xff) * Math.pow((z - s0) / (s1 - s0), 0.5));
					rgb = r << 16 | g << 8 | b;

					theImg.setRGB(x, y, rgb);
				}
		// scale
		BufferedImage scaledImg = new BufferedImage(Math.round(rect.width * pixdim1[0] + margin * 2),
				Math.round(rect.height * pixdim1[1] + margin * 2), BufferedImage.TYPE_INT_RGB);
		AffineTransform at = new AffineTransform();
		at.scale(pixdim1[0], pixdim1[1]);
		at.translate(margin, margin);
		AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
		return scaleOp.filter(theImg, scaledImg);
}

    private BufferedImage drawErrorSlice() {
        BufferedImage theImg = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = theImg.createGraphics();

        g2.setFont(new Font("Helvetica", Font.BOLD, 14));
        g2.drawString("UNAVAILABLE", 15, 64);
        g2.drawRect(1, 1, 126, 126);

        return theImg;
    }

	public void paint(Graphics g) {
		Dimension dim = this.getSize();
		g.setColor(Color.black);
		g.fillRect(0, 0, dim.width, dim.height);

		if (!initialized)
			return;

		if (selectedImage == 0) {
			// All images view
			int i;
			int xoff = 0, yoff = 0, maxHeight;
			scale = QCApp.zoom; // zoom

			maxHeight = 0;
			for (i = 0; i < bufImgList.length; i++) {
				if (xoff + scale * bufImgList[i].getWidth() >= this.getParent().getSize().width) {
					xoff = 0;
					yoff += maxHeight;
					maxHeight = 0;
				}
				rect[i] = new Rectangle(xoff, yoff, (int) (scale * bufImgList[i].getWidth()), (int) (scale * bufImgList[i].getHeight()));
				g.drawImage(bufImgList[i], rect[i].x, rect[i].y, rect[i].width, rect[i].height, null);
				xoff += rect[i].width;
				if (rect[i].height > maxHeight)
					maxHeight = rect[i].height;
			}

			// adjust image size for scroll
			Dimension d = new Dimension(this.getParent().getSize().width, yoff + maxHeight);
			if (!d.equals(this.getParent().getSize())) {
				this.setPreferredSize(d);
				this.revalidate();
			}
		} else {
			// Single volume view

			// adjust image size for scroll
			Dimension d = this.getParent().getSize();
			if (!d.equals(this.getSize())) {
				this.setPreferredSize(d);
				this.revalidate();
				return;
			}
			
			int i = selectedImage - 1;

			// draw image
			rect = Stream.generate(() -> new Rectangle())
				    .limit(imgList.length)
				    .toArray(Rectangle[]::new);
			scale = this.getHeight() / (double) bufImg0.getHeight();
			int xoff, yoff;

			xoff = (int) ((this.getWidth() - bufImg0.getWidth() * scale) / 2.0);
			yoff = 0;
			rect[i] = new Rectangle(xoff, yoff, (int) (scale * bufImg0.getWidth()), (int) (scale * bufImg0.getHeight()));
			g.drawImage(bufImg0, rect[i].x, rect[i].y, rect[i].width, rect[i].height, null);

			g.setColor(Color.white);
			g.drawRoundRect(d.width - 10 - 48, 10, 48, 20, 15, 15);
			String back = "BACK";
			FontMetrics fm = this.getFontMetrics(this.getFont());
			int width = fm.stringWidth(back);
			int height = fm.getHeight();
			g.drawString("BACK", d.width - 10 - 48 + (48 - width) / 2, 10 + 20 - (20 - height) / 2 - 3);

			g.setColor(Color.white);
			g.drawRoundRect(10, 10, 60, 20, 15, 15);
			String toggleStr = toggle ? "HIDE" : "SHOW";
			fm = this.getFontMetrics(this.getFont());
			width = fm.stringWidth(toggleStr);
			g.drawString(toggleStr, 10 + (60 - width) / 2, 10 + 20 - (20 - height) / 2 - 3);
		}
}
					
	public int setImages() {
		return setImages(false);
	}

	public int setImages(boolean init) {
		File f = null; // to avoid the not initialized error
		int i;
		String volName = "";
		MyVolume vol;
		MyVolume volBack;
		int err = 0;
		
		if (imgList == null)
			return 1;

		if (selectedImage == 0) {
			// All images view
			bufImgList = new BufferedImage[imgList.length];
			for (i = 0; i < imgList.length; i++) {
				if (init) {
					String name = subjectDir + "/qc/" + imgList[i].getImageFileName();
					f = new File(name);
					if (f.exists()) {
						// QC images available: load them
						QCApp.printStatusMessage("Loading image \"" + name + "\"...");
						try {
							bufImgList[i] = ImageIO.read(f);
							continue;
						} catch (IOException e) {}
					}
				}
				
				// QC images unavailable: make them (and save them)
				volName = imgList[i].volName;
				vol = volumes.getVolume(new File(subjectDir, volName), imgList[i].color);
				if (vol.volume == null) {
					QCApp.printStatusMessage(
							"ERROR: Volume \"" + new File(subjectDir, volName) + "\" unavailable.");
					bufImgList[i] = drawErrorSlice();
					err = 1;
				} else {
					String volPlane = imgList[i].getPlaneName();
					int plane = imgList[i].getPlane();
					String imgType = imgList[i].effect;
					int cmapindex;

					QCApp.printStatusMessage("Drawing volume \"" + volName + "\", plane:" + volPlane + "...");

					cmapindex = imgList[i].color ? 1 : 0;
					float opacity = QCApp.opacity;

					try {
						if (imgType.equals("3D")) {
							bufImgList[i] = drawVolume(vol, plane, cmapindex);
						} 
						else {
							if (imgList[i].volbackall != null) {
								volBack = volumes.getVolume(new File(subjectDir, imgList[i].volbackall), false);
								bufImgList[i] = drawSlice(vol, volBack, selectedSlice, plane, cmapindex, true, opacity);
							} else
								bufImgList[i] = drawSlice(vol, selectedSlice, plane, cmapindex);
						}
						
						if (init) {
							// save image (create directory qc if it does not exist)
							File qcdir =  new File(subjectDir + "/qc");
							if (!qcdir.exists())
								qcdir.mkdir();
							ImageIO.write(bufImgList[i], "png", f);
						}
					} catch (Exception e) {
						e.printStackTrace();
						err = 1;
						return err;
					} finally {
						QCApp.clearStatusMessage();
					}
				}
			}
		} else {
			// Single volume view
			i = selectedImage - 1;

			// load volume
			volName = imgList[i].volName;
			vol = volumes.getVolume(new File(subjectDir, volName), imgList[i].color);
			int plane = imgList[i].getPlane();
			int cmapindex = imgList[i].color ? 1 : 0;
			float opacity = QCApp.opacity;

			try {
				if (imgList[i].volback != null) {
					volBack = volumes.getVolume(new File(subjectDir, imgList[i].volback), false);
					bufImg0 = drawSlice(vol, volBack, selectedSlice, plane, cmapindex, toggle, opacity);
				} else
					bufImg0 = drawSlice(vol, selectedSlice, plane, cmapindex);
			} catch (Exception e) {
				err = 1;
				return err;
			} finally {
				QCApp.printStatusMessage("");
			}
		}

		repaint();
		updatePositionLabel();
		initialized = true;

		return err;
}
	
	public void updatePositionLabel() {
		if (QCApp.positionLabel != null)
			QCApp.positionLabel.setText(String.format("R:%d A:%d S:%d", selectedSlice[0], selectedSlice[1], selectedSlice[2]));
	}

}
