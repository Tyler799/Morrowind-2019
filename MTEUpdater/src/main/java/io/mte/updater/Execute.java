package io.mte.updater;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;

public class Execute {

	/**
	 *  This method blocks until thread execution until input data is available<br>
	 *  User must press Enter to continue running the application
	 */
	public static void pause() {
		UserInput.waitForEnter();
	}
	/**
	 * Performs a {@code Thread.sleep} using the provided time unit
	 * @param unit seconds, minutes, days...
	 * @param time amount of time to wait
	 */
	public static void wait(TimeUnit unit, long time) {
		try {
			unit.sleep(time);
		} catch(InterruptedException e) {
			Logger.error("Thread was interrupted while sleeping");
		}
	}
	
	/**
	 * Terminate the currently running Java Virtual Machine.<br>
	 * <i>Note that this will prompt the user to continue before closing the java console window</i>
	 * @param code exit status <i>(nonzero value indicates abnormal termination)</i>
	 * @param clean clean all temporary files created while updating
	 */
	public static void exit(int code, boolean clean) {
		exit (code, clean, true);
	}
	
	/**
	 * Terminate the currently running Java Virtual Machine.<br>
	 * @param code exit status <i>(nonzero value indicates abnormal termination)</i>
	 * @param clean clean all temporary files created while updating
	 * @param pause prompt the user to press enter to continue
	 */
	public static void exit(int code, boolean clean, boolean pause) {
		
		if (code == 0) Logger.verbose("Closing updater application...");
		else Logger.print("Terminating updater application...");
		
		if (clean == true)
			FileHandler.updaterCleanup();
		/*
		 *  If we need to pause do it before we close the logfile stream
		 *  otherwise we get errors because we are still trying to print logs
		 */
		if (pause == true && !Main.isLauncher())
			Execute.pause();
		
		Logger.LogFile.close();
		UserInput.close();
		System.exit(code);
	}
	
	/**
	 * Get process id of the currently running Java application
	 * @return numerical value corresponding to the process id
	 */
	public static short getProcessId() {
		String processName = ManagementFactory.getRuntimeMXBean().getName();
		return Short.parseShort(processName.substring(0, processName.indexOf("@")));
	}
	
	private static boolean command(String cmd) {
		
		try {
			Logger.print(Logger.Level.DEBUG, "Excecuting cmd command: %s in a new window", cmd);
			Runtime.getRuntime().exec(cmd, null, null);
			return true;
		} 
		catch (IOException e) {
			Logger.print(Logger.Level.ERROR, e, "Unable to execute Windows command: %s", cmd);
			return false;
		}
	}
	/**
	 * Use {@code Runtime.exec } to start a new program through Windows console.<br> 
	 * If the program is a batch script or java app, it will be opened in a new console window.
	 * 
	 * @param path name or path to the program to start
	 * @return {@code true} if the command executed without errors
	 */
	public static boolean start(Path path) {
		return command("cmd.exe /c start " + path.toString());
	}
	
	/**
	 * Use {@code ProcessBuilder} to start a new application or script process.<br>
	 * <i>Note that if you start a console application this way it will run hidden</i>
	 * 
	 * @param process path to the application or script we want to start
	 * @param wait pause current thread and wait for process to terminate
	 * @param log redirect process input stream to our logfile
	 * @return instance of the process started or {@code null} if an error occurred
	 */
	public static Process start(String process, boolean wait, boolean log) {
		
		Logger.print(Logger.Level.DEBUG, "Starting new process %s" + 
				((wait) ? " and waiting for it to terminate" : ""), process);
		try {
			ProcessBuilder builder = new ProcessBuilder(process);
			Process proc = builder.start();
			if (wait == true) proc.waitFor();
			
			if (log == true) {
				Charset charset = Charset.defaultCharset();
				Logger.LogFile.print(IOUtils.toString(proc.getInputStream(), charset));
			}
			return proc;
		} 
		catch (IOException | InterruptedException e) {
			Logger.print(Logger.Level.ERROR, e, "Unable to start new process %s", process);
			return null;
		}
	}
	/**
	 * Launch a new JVM process inside a console window from an executable JAR.<br>
	 * <i>Note that the jar file should be located inside this app's root directory.</i>
	 * 
	 * @param property system property name
	 * @param value system property value
	 * @param name java jar filename
	 * @param args jvm arguments
	 * @return {@code true} if the process launched successfully, {@code false} otherwise
	 */
	public static boolean launch(String property, String value, String name, String[] args) {
		
		String cmd = "cmd.exe /c start java " + "-D" + property + "=" + value + " -jar " + name;
		for (int i = 0; i <= args.length - 1; i++)
			cmd += " " + args[i];

		return command(cmd);
	}
	
	/**
	 * Find out if process with a given PID is running
	 * @param pid identifier of the process to find
	 * @return {@code true} if the process was found in the tasklist, {@code false} otherwise
	 */
	public static boolean isProcessRunning(String pid)
	{
		try {
			ProcessBuilder processBuilder = new ProcessBuilder("tasklist.exe");
			Process process = processBuilder.start();
			
			Scanner scanner = new Scanner(process.getInputStream(), "UTF-8");
			Scanner scannerRef = scanner.useDelimiter("\\A");
	        String tasksList = scannerRef.hasNext() ? scannerRef.next() : "";
	        scanner.close();
	
			return tasksList.contains(pid);
		}
		catch (IOException e) {
			Logger.print(Logger.Level.ERROR, e, "Unable to find if process %s is running", pid);
			return false;
		}
	}
	/**
	 * Try to kill the process with the given PID through cmd. Wait in intervals of <br>
	 * <i>250ms</i> until the process has terminated or the wait time has elapsed
	 * 
	 * @param pid identifier of the process to terminate
	 * @param wait time in seconds to wait for execution to terminate
	 * @return {@code true} if the process has been killed within the wait time, {@code false} otherwise
	 */
	public static boolean kill(int pid, int wait) {
		
		String sPid = String.valueOf(pid);
		Execute.command("TASKKILL /F /PID " + pid);
		for (long w = TimeUnit.SECONDS.toMillis(wait); isProcessRunning(sPid) && w >= 0; w-=250) 
			wait(TimeUnit.MILLISECONDS, 250); 

		return !isProcessRunning(sPid);
	}
}
