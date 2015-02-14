package tim.prune.function;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import tim.prune.App;
import tim.prune.GenericFunction;
import tim.prune.I18nManager;
import tim.prune.data.Altitude;
import tim.prune.data.Field;
import tim.prune.data.SourceInfo;
import tim.prune.data.Track;
import tim.prune.load.TrackNameList;

/**
 * Function to allow the selection of which tracks to load from the file / stream
 */
public class SelectTracksFunction extends GenericFunction
{
	private Field[] _fieldArray = null;
	private Object[][] _dataArray = null;
	private Altitude.Format _altFormat = Altitude.Format.NO_FORMAT;
	private SourceInfo _sourceInfo = null;
	private TrackNameList _trackNameList = null;
	private JDialog _dialog = null;
	private JList _trackList = null;

	/**
	 * Constructor
	 * @param inApp app object to use for load
	 * @param inFieldArray field array
	 * @param inDataArray data array
	 * @param inAltFormat altitude format
	 * @param inSourceInfo source information
	 * @param inTrackNameList track name list
	 */
	public SelectTracksFunction(App inApp, Field[] inFieldArray, Object[][] inDataArray,
		Altitude.Format inAltFormat, SourceInfo inSourceInfo, TrackNameList inTrackNameList)
	{
		super(inApp);
		_fieldArray = inFieldArray;
		_dataArray = inDataArray;
		_altFormat = inAltFormat;
		_sourceInfo = inSourceInfo;
		_trackNameList = inTrackNameList;
	}

	/**
	 * Start the function
	 */
	public void begin()
	{
		_dialog = new JDialog(_parentFrame, I18nManager.getText("function.open"));
		_dialog.setLocationRelativeTo(_parentFrame);
		_dialog.getContentPane().add(makeContents());
		_dialog.pack();
		_dialog.setVisible(true);
		selectAll();
	}

	/**
	 * @return the contents of the window as a Component
	 */
	private Component makeContents()
	{
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BorderLayout());
		mainPanel.add(new JLabel(I18nManager.getText("dialog.selecttracks.intro")), BorderLayout.NORTH);
		// track list
		final int numTracks = _trackNameList.getNumTracks();
		String[] names = new String[numTracks];
		for (int i=0; i<numTracks; i++)
		{
			String name = _trackNameList.getTrackName(i);
			if (name == null || name.equals("")) {
				name = I18nManager.getText("dialog.selecttracks.noname");
			}
			names[i] = name + " (" + _trackNameList.getNumPointsInTrack(i) + ")";
		}
		_trackList = new JList(names);
		_trackList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		mainPanel.add(new JScrollPane(_trackList), BorderLayout.CENTER);
		// select all button
		JButton selectAllButton = new JButton(I18nManager.getText("button.selectall"));
		selectAllButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				selectAll();
			}
		});
		JPanel eastPanel = new JPanel();
		eastPanel.add(selectAllButton);
		mainPanel.add(eastPanel, BorderLayout.EAST);

		// button panel at bottom
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		JButton okButton = new JButton(I18nManager.getText("button.ok"));
		okButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				finish();
			}
		});
		buttonPanel.add(okButton);
		JButton cancelButton = new JButton(I18nManager.getText("button.cancel"));
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				_dialog.dispose();
				_app.informNoDataLoaded();
			}
		});
		buttonPanel.add(cancelButton);
		mainPanel.add(buttonPanel, BorderLayout.SOUTH);
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 15));
		return mainPanel;
	}

	/** @return name key */
	public String getNameKey() {
		return "dialog.selecttracks";
	}

	/**
	 * Select all the tracks in the list
	 */
	private void selectAll()
	{
		_trackList.setSelectionInterval(0, _trackNameList.getNumTracks()-1);
	}

	/**
	 * OK pressed, so finish the load
	 */
	private void finish()
	{
		_dialog.dispose();
		// Load track as it is, which is expensive but makes interrogating waypoints easier
		Track loadedTrack = new Track();
		loadedTrack.load(_fieldArray, _dataArray, _altFormat);
		int[] tracks = _trackList.getSelectedIndices();
		// Check if all tracks are selected, then don't have to filter at all
		if (tracks.length == _trackNameList.getNumTracks()) {
			_app.informDataLoaded(loadedTrack, _sourceInfo);
		}
		else
		{
			// build array of which tracks have been selected
			boolean[] selectedTracks = new boolean[_trackNameList.getNumTracks()];
			for (int i=0; i<tracks.length; i++) {
				selectedTracks[tracks[i]] = true;
			}
			// Loop over all points, counting points which survive filter and making flag array
			int numPointsSelected = 0;
			int currentTrack = -1;
			final int totalPoints = loadedTrack.getNumPoints();
			boolean[] selectedPoints = new boolean[totalPoints];
			for (int i=0; i<totalPoints; i++)
			{
				final int startOfNextTrack = _trackNameList.getStartIndex(currentTrack+1);
				if (i == startOfNextTrack) {currentTrack++;}
				if (currentTrack < 0 || selectedTracks[currentTrack] || loadedTrack.getPoint(i).isWaypoint()) {
					selectedPoints[i] = true;
					numPointsSelected++;
				}
			}
			// If none of the points have been selected, then load nothing
			if (numPointsSelected <= 0) {
				_app.informNoDataLoaded();
			}
			else {
				// Create new data array of required length
				Object[][] newDataArray = new String[numPointsSelected][];
				// Loop over all points again, copying surviving points
				int currPoint = 0;
				for (int i=0; i<totalPoints; i++)
				{
					if (selectedPoints[i]) {
						newDataArray[currPoint] = _dataArray[i];
						currPoint++;
					}
				}
				// Construct Track and call informDataLoaded
				Track filteredTrack = new Track();
				filteredTrack.load(_fieldArray, newDataArray, _altFormat);
				// Tell source info object which points were selected (pass selectedPoints array)
				_sourceInfo.setPointIndices(selectedPoints);
				_app.informDataLoaded(filteredTrack, _sourceInfo);
			}
		}
	}
}
