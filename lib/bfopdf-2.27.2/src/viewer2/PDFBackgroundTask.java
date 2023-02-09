// $Id: PDFBackgroundTask.java 10689 2009-08-12 15:25:36Z mike $

package org.faceless.pdf2.viewer2;

/**
 * This interface should be implemented  by any features in
 * the Viewer that run in a background thread. These features
 * need to be paused when an exclusive lock on the whole PDF
 * is required, such as when the PDF is saved. An example of
 * one of these objects is the SidePanel returned from
 * {@link org.faceless.pdf2.viewer2.feature.ThumbnailPanel}
 * @since 2.11.7
 */
public interface PDFBackgroundTask {

    /**
     * Return true if the task has been paused with the {@link #pause} method
     */
    public boolean isPaused();

    /**
     * Pause the task until the {@link #unpause} method is called.
     * This method waits until the task has actually paused.
     */
    public void pause() throws InterruptedException;

    /**
     * Unpause the task after a call to {@link #pause}
     */
    public void unpause();

    /**
     * Returns true if the task is still running
     */
    public boolean isRunning();
}
