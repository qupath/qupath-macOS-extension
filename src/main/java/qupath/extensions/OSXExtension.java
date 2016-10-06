/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.extensions;

import com.apple.eawt.Application;
import com.apple.eawt.AppEvent.OpenFilesEvent;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;

/**
 * Simple extension that enables QuPath to open data files when double-clicked on macOS (OS X).
 * 
 * If this should work to open QuPath in the first place, it depends upon calling 
 * setOSXFileHandler() at an early stage (before the JavaFX application is launched),
 * and then use installExtension(qupath) later to trigger the file to actually be opened.
 * 
 * This suggests there may later be a rather hackish workaround that makes use of reflection in the launcher class...
 * although a more elegant solution would be desirable.
 * 
 * However, for now, installing this extension alone will only mean files can be 
 * opened within QuPath only if QuPath is already running.
 * 
 * @author Pete Bankhead
 *
 */
@SuppressWarnings("restriction")
public class OSXExtension implements QuPathExtension {
	
	private final static Logger logger = LoggerFactory.getLogger(OSXExtension.class);
	
	private static File pendingFile;
	private static boolean alreadyLoaded = false;

	@Override
	public void installExtension(QuPathGUI qupath) {
		// Don't do anything if we don't have a Mac
		if (!GeneralTools.isMac()) {
			logger.warn("Attempted to add Mac extension on a non-Mac platform - will be ignored...");
			return;
		}
		
		if (alreadyLoaded) {
			// If we've already loaded this, then put in a request to open any pending file
			// This is posted for future processing since we might still be initializing the GUI -
			// we'd like to run the command after the GUI is visible
			// (However, because of when extensions are loaded, this doesn't occur without some extra work involved)
			if (pendingFile != null) {
				logger.info("Loading file", pendingFile);
				Platform.runLater(() -> qupath.openImage(pendingFile.getAbsolutePath(), false, false, false));
			}
			return;
		}
		setOSXFileHandler();
	}
	
	
	/**
	 * Set a QuPath file handler for the OSX application.
	 * 
	 * See https://docs.oracle.com/javase/tutorial/deployment/selfContainedApps/fileassociation.html for more information
	 */
	public static void setOSXFileHandler() {
		if (alreadyLoaded)
			return;
		
		logger.debug("Setting macOS file handler");
		
		Application app = Application.getApplication();
		
		app.setOpenFileHandler((OpenFilesEvent event) -> {
			List<?> files = event.getFiles();
			logger.debug("Open files: {}", files);
			if (files != null && files.size() > 0) {
				// Requested files can actually pile up if the extension is added late...
				// so we want the one at the end of the list
				Object file = files.get(files.size()-1);
				if (file instanceof File) {
					String path = ((File)file).getAbsolutePath();
					logger.info("Opening path: {}", path);
					QuPathGUI qupath = QuPathGUI.getInstance();
					if (qupath == null || qupath.getStage() == null || !qupath.getStage().isShowing()) {
						logger.warn("No QuPath instance available!");
						pendingFile = (File)file;
						return;
					} else {
						Platform.runLater(() -> {
							qupath.openImage(path, false, false, false);
						});
					}
				}
			}
		});
		// Avoid doing things twice...
		alreadyLoaded = true;
	}
	
	@Override
	public String getName() {
		return "macOS extension";
	}

	@Override
	public String getDescription() {
		return "Adapts QuPath to behave more like a native macOS application";
	}

	
}