import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
// import java.util.Date;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;

class MyVolume {
    final static short DT_UINT8 = 2;
    final static short DT_INT16 = 4;
    final static short DT_INT32 = 8;
    final static short DT_FLOAT32 = 16;

	byte[][][] volume; 						// 3d volume data
	int[] dim = new int[3]; 				// 3d volume dimensions
	float[] pixdim = new float[3]; 			// 3d volume pixel dimensions
	short datatype; 						// 3d volume data type
	int[][] boundingBox = new int[2][6]; 	// 3d volume bounding box
	float[][] R = new float[3][4]; 			// coordinate systems matrix
	
	//int maxval;
	boolean color;

    File file;
    ByteOrder BYTE_ORDER;

    int bytesPerVoxel() {
        int bpv=0;
        switch(datatype)
        {
            case DT_UINT8:		bpv=1; break;
            case DT_INT16:		bpv=2; break;
            case DT_INT32:		bpv=4; break;
            case DT_FLOAT32:	bpv=4; break;
        }
        return bpv;
    }

    private static int[] linspace(int min, int max, int points) {
        int[] d = new int[points];
        for (int i = 0; i < points; i++){
            d[i] = min + i * (max - min) / (points - 1);
        }
        return d;
    }

    void loadFreeSurferVolume(DataInputStream dis) throws IOException {
        BYTE_ORDER = ByteOrder.BIG_ENDIAN;
        final int HEADER_SIZE = 284;
        final int MGHUCHAR = 0;
        final int MGHINT = 1;
        final int MGHFLOAT = 3;
        final int MGHSHORT = 4;

		// Read volume data
		byte b[] = new byte[HEADER_SIZE]; // total header size
		ByteBuffer bb = ByteBuffer.wrap(b);

		dis.readFully(b, 0, HEADER_SIZE);

		bb.order(BYTE_ORDER);
		dim[0] = bb.getInt(4);
		dim[1] = bb.getInt(8);
		dim[2] = bb.getInt(12);
		int mghtype = bb.getInt(20);
		switch (mghtype) {
		case MGHUCHAR:
			datatype = DT_UINT8;
			break;
		case MGHSHORT:
			datatype = DT_INT16;
			break;
		case MGHINT:
			datatype = DT_INT32;
			break;
		case MGHFLOAT:
			datatype = DT_FLOAT32;
			break;
		}
		pixdim[0] = bb.getFloat(30);
		pixdim[1] = bb.getFloat(34);
		pixdim[2] = bb.getFloat(38);
		
		R[0][0] = bb.getFloat(42);
		R[1][0] = bb.getFloat(46);
		R[2][0] = bb.getFloat(50);
		R[0][1] = bb.getFloat(54);
		R[1][1] = bb.getFloat(58);
		R[2][1] = bb.getFloat(62);
		R[0][2] = bb.getFloat(66);
		R[1][2] = bb.getFloat(70);
		R[2][2] = bb.getFloat(74);
		R[0][3] = bb.getFloat(78);
		R[1][3] = bb.getFloat(82);
		R[2][3] = bb.getFloat(86);

        loadVolume(dis);
    }

    void loadNiftiVolume(DataInputStream dis) throws IOException {
        // float[][] R = new float[3][3];
        final int HEADER_SIZE = 348;
        BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

        // Read volume data
        byte by[] = new byte[HEADER_SIZE]; // total header size
        ByteBuffer bb = ByteBuffer.wrap(by);
        dis.readFully(by, 0, HEADER_SIZE);
        bb.order(BYTE_ORDER);
        short dim0 = bb.getShort(40);
        if (dim0 > 15) {
            BYTE_ORDER = ByteOrder.BIG_ENDIAN;
            bb.order(BYTE_ORDER);
        }

        dim[0] = bb.getShort(42);
        dim[1] = bb.getShort(44);
        dim[2] = bb.getShort(46);
        datatype = bb.getShort(70);
        pixdim[0] = bb.getFloat(80);
        pixdim[1] = bb.getFloat(84);
        pixdim[2] = bb.getFloat(88);

        short qform_code = bb.getShort(0xFC);
        if (qform_code < 1) {
            QCApp.printStatusMessage("Error: qform_code < 1");
            return;
        }

        float qb = bb.getFloat(0x100);
        float qc = bb.getFloat(0x104);
        float qd = bb.getFloat(0x108);
        float qq = bb.getFloat(76);

        float b = qb;
        float c = qc;
        float d = qd;
        float q = Math.round(qq);
        float a = (float) Math.sqrt(Math.max(1 - b*b - c*c - d*d, 0));

        R[0][0] = Math.round(a*a + b*b - c*c - d*d);
        R[0][1] = Math.round(2 * (b*c - a*d));
        R[0][2] = Math.round(2 * (b*d + a*c) * q);
        R[1][0] = Math.round(2 * (b*c + a*d));
        R[1][1] = Math.round(a*a + c*c - b*b - d*d);
        R[1][2] = Math.round(2 * (c*d - a*b) * q);
        R[2][0] = Math.round(2 * (b*d - a*c));
        R[2][1] = Math.round(2 * (c*d + a*b));
        R[2][2] = Math.round((a*a + d*d - b*b - c*c) * q);

        float vox_offset = bb.getFloat(108);
        dis.skipBytes((int)vox_offset - HEADER_SIZE);

        loadVolume(dis);
    }

    void loadVolume(DataInputStream dis) throws IOException {
        ByteBuffer bb;
        float mult;

        ReadableByteChannel chan = Channels.newChannel(dis);
        bb = ByteBuffer.allocate(dim[0] * dim[1] * dim[2] * bytesPerVoxel());
        chan.read(bb);
        bb.rewind();
        bb.order(BYTE_ORDER);

        int size = dim[0]*dim[1]*dim[2];
        System.out.println("Allocating " + size + " bytes");
        volume = new byte[dim[2]][dim[1]][dim[0]];
        IntStream ist;
        int values[];
        switch (datatype) {
        case DT_UINT8:
            ist = IntStream.range(0, size).map(i -> bb.get() & 0xFF);
            break;
        case DT_INT16:
            ist = IntStream.range(0, size).map(i -> bb.getShort());
            break;
        case DT_INT32:
            ist = IntStream.range(0, size).map(i -> bb.getInt());
            break;
        case DT_FLOAT32:
            ist = IntStream.range(0, size).map(i -> (int) bb.getFloat());
            break;
        default:
            QCApp.printStatusMessage("Error unknown data type for volume: " + "volName");
            return;
        }
        values = ist.toArray();
        chan.close();
        
		int[] values2 = Arrays.copyOf(values, values.length);
        
		Arrays.sort(values2);
        
		float maxval = values2[(int)(dim[0]*dim[1]*dim[2]*.98)];
		
		// Convert values from int to byte for using less memory
		if (color)
			mult = 1;
		else
			mult = 255 / maxval;
        
		for (int k = 0; k < dim[2]; k += 1)
			for (int j = 0; j < dim[1]; j += 1)
				for (int i = 0; i < dim[0]; i += 1) {
					int value = (int) (values[k*dim[1]*dim[0] + j*dim[0] + i] * mult);
					if (value > 0 && value <= 255)
						volume[k][j][i] = (byte) value;
					else if (value > 255)
						volume[k][j][i] = (byte) 255;
				}
        
		// Get bounding boxes and maxval
		for (int m=0; m<boundingBox.length; m++) {
			boundingBox[m][0] = dim[0] - 1; // min i
			boundingBox[m][1] = 0; // max i
			boundingBox[m][2] = dim[1] - 1; // min j
			boundingBox[m][3] = 0; // max j
			boundingBox[m][4] = dim[2] - 1; // min k
			boundingBox[m][5] = 0; // max k
			int val;
			int rgb;
			int s = 40; // there's no need to scan all voxels...
			int[] is = linspace(0, dim[0]-1, s);
			int[] js = linspace(0, dim[1]-1, s);
			int[] ks = linspace(0, dim[2]-1, s);
			for (int k = 1; k < ks.length - 1; k++)
				for (int j = 1; j < js.length - 1; j++)
					for (int i = 1; i < is.length - 1; i++) {
						val = volume[ks[k]][js[j]][is[i]];
						if (m > 0) {
							if (val < 0 || val > 255)
								continue;
							rgb = MyImages.cmap[m][val];
						}
						else
							rgb = val;
						if (rgb > 0) {
							if (is[i-1] < boundingBox[m][0])
								boundingBox[m][0] = is[i-1];
							if (is[i+1] > boundingBox[m][1])
								boundingBox[m][1] = is[i+1];
							if (js[j-1] < boundingBox[m][2])
								boundingBox[m][2] = js[j-1];
							if (js[j+1] > boundingBox[m][3])
								boundingBox[m][3] = js[j+1];
							if (ks[k-1] < boundingBox[m][4])
								boundingBox[m][4] = ks[k-1];
							if (ks[k+1] > boundingBox[m][5])
								boundingBox[m][5] = ks[k+1];
						}
					}
		}
	}

	public int getValue(int i, int j, int k)
	// get value at voxel with index coordinates i,j,k
	{
		if (i >= 0 && i < dim[0] && j >= 0 && j < dim[1] && k >= 0 && k < dim[2])
			return volume[k][j][i] & 0xFF;
		else
			return 0;
	}
        
	public MyVolume(File file, boolean color) {
		InputStream is = null;
		this.file = file;
		this.color = color;
		String fileName = file.getPath();
        
		try {
	        is = new FileInputStream(file);
			if (fileName.matches(".*\\.m?gz"))
	            is = new GZIPInputStream(is);
			is = new DataInputStream(is);
			
			if (fileName.matches(".*\\.mg[hz]"))
				loadFreeSurferVolume((DataInputStream) is);
			else if (fileName.matches(".*\\.nii(\\.gz)?"))
				loadNiftiVolume((DataInputStream) is);
			else
				QCApp.printStatusMessage("Error unknown file type: " + "volName");
		} catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			try {
				is.close();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
