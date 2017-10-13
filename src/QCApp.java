
// QCApp 3, 13 Oct 2017

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.LayoutStyle;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.io.FileHandler;
import org.apache.commons.configuration2.tree.ImmutableNode;


public class QCApp {

	static private class MyTableModel extends DefaultTableModel {

		private static final long serialVersionUID = -3092232013219096243L;

		@Override
	    public boolean isCellEditable(int rowIndex, int columnIndex) {
	        
	        //here the columns 0 and 2 are non-editable
	        if (columnIndex == 0 || columnIndex == 2) return false;
	        
	        //the rest is editable
	        return true;
	    }
	}

	private static JFrame f;
	private static JTextArea status;
	private static JButton chooseButton;
	private static JButton loadButton;
	private static JButton saveButton;
	private static JComboBox<String> configList;
	private static JLabel configListLabel;
	private static JTable table;
	private static MyTableModel model;
	private static MyImages images;
	private static MyGraphs graphs;
	private static File subjectsDir;
	private static String colorLUTFile;
	private static List<File> configFiles;
	private static Path configDir;
	private static String qcfile;

	public static JLabel positionLabel;
	
	private static JSlider zoomSlider;
	private static JLabel zoomLabel;
	private static JLabel zoomValueLabel;
	
	public static double zoom;
	
	public static List<RegionColor> colorLUT;
	public static List<HierarchicalConfiguration<ImmutableNode>> imageConfigs;
	
	public static Object imagesMap;
	public static String statsFileName;
	public static String[] measures;
	
	private static boolean tableChanged;

	public static void printStatusMessage(String msg) {
		if (QCApp.status != null) {
			QCApp.status.setText(msg);
			QCApp.status.paintImmediately(QCApp.status.getVisibleRect());
		} else
			System.out.println(msg);
	}
	
	public static void setTableChanged(boolean tableChanged) {
		QCApp.tableChanged = tableChanged;
		f.getRootPane().putClientProperty("Window.documentModified", tableChanged);
	}
	
	public static void loadConfiguration(File configFile) throws ConfigurationException, IOException {
		InputStream input;
		// input = getResourceAsStream(configDir + "/" + configFile);
		input = new FileInputStream(configFile);
		XMLConfiguration config = new XMLConfiguration();
		FileHandler fh = new FileHandler(config);
		fh.load(input);
		colorLUTFile = config.getString("colorLUT");
		imageConfigs = config.configurationsAt("images.image");
		statsFileName = config.getString("stats.file");
		measures = config.getStringArray("stats.measures.measure");
		qcfile = config.getString("qcfile");

		// init segmentation label colourmap
		colorLUT = new ArrayList<RegionColor>();
    	input = Files.newInputStream(Paths.get(configDir.toString(), colorLUTFile));
    	String line;
    	BufferedReader buffer = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    	while ((line = buffer.readLine()) != null) {
			if (line.startsWith("#"))
				continue;
            String[] parts = line.split("\\s+");
            if (parts.length < 5)
            	continue;
            int No = Integer.valueOf(parts[0]);
            if (No >= 0 && No < 255) {
                RegionColor regionColor = new RegionColor();
                regionColor.No = No;
                regionColor.label = parts[1];
                regionColor.R = Integer.valueOf(parts[2]);
                regionColor.G = Integer.valueOf(parts[3]);
                regionColor.B = Integer.valueOf(parts[4]);
                colorLUT.add(regionColor);
            }
        }
	}

	public static void selectSubject(JTable table) {
		if (table.getSelectedRowCount() == 0)
			return;
		int i = table.getSelectedRows()[0];
		String subject = model.getValueAt(i, 2).toString();

		printStatusMessage("Subject: " + subject + ".");
		images.changeSubjectDir(subjectsDir + "/" + subject);

		graphs.setSelectedSubject(subject);
	}

	public static void chooseDirectory() {
		List<String> subjects = new ArrayList<String>();
		if (tableChanged) {
	        int ans = JOptionPane.showConfirmDialog(f, 
	                "Do you want to save QC before changing subject directory?", "Save QC?", 
	                JOptionPane.YES_NO_CANCEL_OPTION,
	                JOptionPane.QUESTION_MESSAGE);
	        if (ans == JOptionPane.YES_OPTION) {
	        	if (!saveQC())
	        		return;
	        } else if (ans == JOptionPane.CANCEL_OPTION)
		    	return;
		}

		// select Subjects directory
		final JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Choose Subjects Directory...");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setAccessory(configList);
		if (subjectsDir != null) {
			fc.setSelectedFile(subjectsDir);
		}
		int returnVal = fc.showOpenDialog(null);
		if (returnVal != JFileChooser.APPROVE_OPTION)
			return;
		subjectsDir = fc.getSelectedFile();
		printStatusMessage("Subjects Directory: " + subjectsDir + ".");
		
		// load selected configuration
		try {
			loadConfiguration(configFiles.get(configList.getSelectedIndex()));
		} catch (ConfigurationException | IOException e) {
			e.printStackTrace();
			return;
		}

		// clear table
		model.setRowCount(0);

		// add files to table
		File files[] = subjectsDir.listFiles();
		Arrays.sort(files);
		Vector<Object> row;
		int n = 1;
		for (int i = 0; i < files.length; i++) {
			if (!new File(files[i] + "/" + statsFileName).isFile())
				continue;

			printStatusMessage("Creating QC table... " + (i + 1) + "/" + files.length);

			String subject = files[i].getName();
			subjects.add(subject);

			row = new Vector<Object>();
			row.add(n);
			row.add(0);
			row.add(subject);
			row.add("");
			model.addRow(row);
			n++;
		}
		
		// configure stats graphs
		graphs.configure(subjectsDir, subjects);
		
		// if there is a QC file inside subjectsDir, load it.
		File f = new File(subjectsDir, qcfile);
		if (f.exists()) {
			System.out.println(qcfile + " file present, read it.");
			readQC(f);
		}
		setTableChanged(false);

		printStatusMessage(model.getRowCount() + " subjects read.");
		if (model.getRowCount() > 0) {
			loadButton.setEnabled(true);
			saveButton.setEnabled(true);
//			zoomSlider.setVisible(true);
//			zoomLabel.setVisible(true);
//			zoomValueLabel.setVisible(true);
			images.renew();
			table.changeSelection(0, 0, false, false);
			table.requestFocus();
		}
	}
	
	public static void readQC(File file) {
		BufferedReader input;
		int qc;
		String sub;
		String comment;
		String line;
		int i, j, k, l;
		try {
			input = new BufferedReader(new FileReader(file));
			input.readLine(); // skip header row
			
			while ((line = input.readLine()) != null) {
				try {
					j = line.indexOf("\t");
					k = line.indexOf("\t", j + 1);
					l = line.indexOf("\t", k + 1);
					sub = line.substring(0, j);
					qc = Integer.parseInt(line.substring(j + 1, k));
					comment = line.substring(k + 1, l);
					
				    for (i = model.getRowCount() - 1; i >= 0; --i)
			            if (sub.equals(model.getValueAt(i, 2).toString()))
			                // what if value is not unique?
			                break;
					
					if (i < 0) {
						System.out.println("Warning: " + qcfile + "subject " + sub + " not found in subjects directory.");
						printStatusMessage("Warning: " + qcfile + "subject " + sub + " not found in subjects directory.");
						continue;
					}
					model.setValueAt(qc, i, 1);
					model.setValueAt(comment, i, 3);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			input.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void setQC(String subject, String msg) {
		int i;
		for (i = 0; i < model.getRowCount(); i++)
			if (model.getValueAt(i, 2).toString().equals(subject)) {
				model.setValueAt(0, i, 1);
				model.setValueAt(msg, i, 3);
				break;
			}
	}

	public static boolean saveQC() {
		// Save QC
		final JFileChooser fc = new JFileChooser();
		fc.setCurrentDirectory(subjectsDir);
		fc.setSelectedFile(new File(qcfile));
		fc.setDialogTitle("Save QC File...");
		int returnVal = fc.showSaveDialog(null);
		if (returnVal != JFileChooser.APPROVE_OPTION)
			return false;

		File file = fc.getSelectedFile();
		try {
			int i, j;
			double x[] = new double[MyGraphs.NB_REGIONS];
			Writer output = new BufferedWriter(new FileWriter(file));
			String sub;

			output.write("Subject\tQC\tComments\t");
			for (j = 0; j < MyGraphs.NB_REGIONS; j++) {
				if (graphs.mean[j] == 0)
					continue;
				if (j < MyGraphs.NB_REGIONS - 1)
					output.write(MyGraphs.regions.get(j) + "\t");
				else
					output.write(MyGraphs.regions.get(j) + "\n");
			}

			for (i = 0; i < model.getRowCount(); i++) {
				sub = model.getValueAt(i, 2).toString();
				output.write(sub + "\t"); // Subject
				output.write(model.getValueAt(i, 1).toString() + "\t"); // QC
				output.write(model.getValueAt(i, 3).toString() + "\t"); // Comments
				
				printStatusMessage("Saving volumetric data for subject " + (i + 1) + "/" + model.getRowCount());
				graphs.getVolumesForSubject(sub, x); // Volumes
				for (j = 0; j < MyGraphs.NB_REGIONS; j++) {
					if (graphs.mean[j] == 0)
						continue;
					if (j < MyGraphs.NB_REGIONS - 1)
						output.write(x[j] + "\t");
					else
						output.write(x[j] + "\n");
				}
			}
			output.close();
			setTableChanged(false);
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(f, "Failed to save QC file: " + file, "Save failed", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		printStatusMessage("QC file saved.");
		return true;
	}
	
	public static void loadQC() {
		// Load QC
		final JFileChooser fc = new JFileChooser();
		fc.setCurrentDirectory(subjectsDir);
		fc.setSelectedFile(new File(qcfile));
		fc.setDialogTitle("load QC File...");
		int returnVal = fc.showOpenDialog(null);
		if (returnVal != JFileChooser.APPROVE_OPTION)
			return;
		
		File file = fc.getSelectedFile();
		System.out.println("Loading QC file " + file.getName());
		readQC(file);
		setTableChanged(false);
	}
	
	private static void updateZoom() {
		zoom = zoomSlider.getValue()/100.;
		zoomValueLabel.setText("x" + String.format("%.2f", zoom));
		images.repaint();
	}

	public static void createAndShowGUI() {
		f = new JFrame("QCApp");
		f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				if (tableChanged) {
			        int ans = JOptionPane.showConfirmDialog(f, 
			                "Do you want to save QC before closing?", "Save QC?", 
			                JOptionPane.YES_NO_CANCEL_OPTION,
			                JOptionPane.QUESTION_MESSAGE);
			        if (ans == JOptionPane.YES_OPTION) {
			        	if (saveQC())
			        		System.exit(0);
			        } else if (ans == JOptionPane.NO_OPTION)
				    	System.exit(0);
				} else {
			        int ans = JOptionPane.showConfirmDialog(f, 
			                "Are you sure to close this window?", "Really Closing?", 
			                JOptionPane.YES_NO_OPTION,
			                JOptionPane.QUESTION_MESSAGE);
			        if (ans == JOptionPane.YES_OPTION)
			        	System.exit(0);
				}
			}
		});

		// Status text
		status = new JTextArea("Choose a Subjects Directory");
		status.setOpaque(false);

		// Choose Button
		chooseButton = new JButton("Choose Subjects Directory...");
		chooseButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				chooseDirectory();
			}
		});

		// Save Button
		saveButton = new JButton("Save QC...");
		saveButton.setEnabled(false);
		saveButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				saveQC();
			}
		});

		// Load Button
		loadButton = new JButton("Load QC...");
		loadButton.setEnabled(false);
		loadButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				loadQC();
			}
		});
		
		// Configuration Combo Box
		String[] configNames = configFiles.stream().map(file -> file.getName().substring(0, file.getName().length() - 4)).toArray(String[]::new);
		configList = new JComboBox<String>(configNames);

		// Configuration Combo Box Label
		configListLabel = new JLabel("Configuration: ");
		configListLabel.setLabelFor(configList);
		
		// Position Label
		positionLabel = new JLabel();
		
		// Zoom slider
		zoomSlider = new JSlider(JSlider.HORIZONTAL, 50, 300, 200);
		zoomSlider.setMajorTickSpacing(50);
		zoomSlider.setMinorTickSpacing(5);
		zoomSlider.setPaintTicks(false);
		zoomSlider.setPaintLabels(false);
		zoomSlider.setSnapToTicks(true);
		zoomSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updateZoom();
			}
		});
		//zoom.setVisible(true);
		
		zoomLabel = new JLabel("Zoom: ");
		//zoomLabel.setVisible(false);
		zoomValueLabel = new JLabel();
		//zoomValueLabel.setVisible(false);
		//zoomLabel.setLabelFor(zoom);

		// Table
		model = new MyTableModel();
		table = new JTable(model);
		model.addColumn("#");
		model.addColumn("QC");
		model.addColumn("Subject");
		model.addColumn("Comments");
		model.addTableModelListener(new TableModelListener() {
			public void tableChanged(TableModelEvent e) {
				if (e.getColumn() >= 0)
					setTableChanged(true);
			}
		});
		table.setCellSelectionEnabled(true);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setPreferredScrollableViewportSize(new Dimension(250, 70));
		table.setFillsViewportHeight(true);
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				if (!e.getValueIsAdjusting())
					selectSubject(table);
			}
		});
		JScrollPane scrollPane = new JScrollPane(table);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		table.getColumnModel().getColumn(0).setMinWidth(32);
		table.getColumnModel().getColumn(1).setMinWidth(32);
		table.getColumnModel().getColumn(2).setPreferredWidth(800);
		table.getColumnModel().getColumn(3).setPreferredWidth(800);

		// Graphs
		graphs = new MyGraphs();
		graphs.setPreferredSize(new Dimension(250, 250));

		// Image
		images = new MyImages();
		JScrollPane imagesScrollPane = new JScrollPane(images);
		
		updateZoom();

		// Split Pane for Table and Graphs
		JSplitPane splitPaneForTableAndGraphs = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollPane, graphs);
		splitPaneForTableAndGraphs.setOneTouchExpandable(true);
		splitPaneForTableAndGraphs.setDividerLocation(350);
		splitPaneForTableAndGraphs.setResizeWeight(1.0);

		// Split Pane for the previous and Images
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, splitPaneForTableAndGraphs,
				imagesScrollPane);
		splitPane.setOneTouchExpandable(true);
		splitPane.setDividerLocation(350);

		// Layout the GUI
		GroupLayout layout = new GroupLayout(f.getContentPane());
		f.getContentPane().setLayout(layout);
		layout.setAutoCreateGaps(true);
		layout.setAutoCreateContainerGaps(true);
		layout.setHorizontalGroup(layout.createParallelGroup()
				.addGroup(layout.createSequentialGroup()
						.addComponent(chooseButton)
						.addComponent(loadButton)
						.addComponent(saveButton)
						.addComponent(zoomLabel)
						.addComponent(zoomSlider, 50, 100, 100)
						.addComponent(zoomValueLabel)
						.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
						//.addComponent(configListLabel)
						//.addComponent(configList)
						.addComponent(positionLabel)
						)
				.addComponent(splitPane)
				.addComponent(status, GroupLayout.Alignment.LEADING));
		layout.setVerticalGroup(layout.createSequentialGroup()
				.addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
						.addComponent(chooseButton)
						.addComponent(loadButton)
						.addComponent(saveButton)
						.addComponent(zoomLabel)
						.addComponent(zoomSlider)
						.addComponent(zoomValueLabel)
						//.addComponent(configList)
						//.addComponent(configListLabel)
						.addComponent(positionLabel)
						)
				.addComponent(splitPane)
				.addComponent(status, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE));
		f.pack();
		f.setVisible(true);
	}

	public static void main(String[] args) throws NumberFormatException, IOException, ConfigurationException{
		
		configDir = Paths.get(QCApp.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent().resolve("config");
		configFiles = Files.list(configDir)
			     .filter(Files::isRegularFile)
			     .filter(path -> path.toString().endsWith(".xml"))
			     .map(Path::toFile)
			     .sorted(Comparator.comparing(file -> file.getName().substring(0, file.getName().length() - 4)))
			     .collect(Collectors.toList());
		
    	
		if (args.length == 1) {
			File dir = new File(args[0]);
			//new MyImages(dir.getPath());
			images.changeSubjectDir(dir.getPath());
		} else {
			new QCApp();
			createAndShowGUI();
		}
	}
}