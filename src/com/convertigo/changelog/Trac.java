package com.convertigo.changelog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

public class Trac {

	public static void main(String[] args) {
		try {
			Trac tracChangelog = new Trac(args);
			tracChangelog.computeChangelog();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private class ChangelogEntry {
		public ChangelogEntry(int id, String comment, String supportCaseId) {
			// this.id = id;
			this.comment = comment;
			this.supportCaseId = supportCaseId;
		}

		// public int id;
		public String supportCaseId;
		public String comment;
	}

	private String tracQueryMilestonesURL = "http://devus.twinsoft.fr/report/30?format=tab";

	private String tracQueryChangelogURL = "http://devus.twinsoft.fr/query?" + "status=closed&"
			+ "resolution=fixed&" + "group=type&" + "format=tab&" + "max=10000&" + "order=priority&"
			+ "col=id&" + "col=type&" + "col=changelog_comment&" + "col=changelog_support_case&"
			+ "add_to_changelog=yes&" + "type=!task&" + "milestone=%product%+%milestone%";

	private enum ChangelogEntryTypes {
		BUG("bug"), IMPROVEMENT("improvement"), NEW_FEATURE("new feature");

		final private String sType;

		private ChangelogEntryTypes(String sType) {
			this.sType = sType;
		}

		public String toString() {
			return sType;
		}
	};

	private List<ChangelogEntry> listBugs;
	private List<ChangelogEntry> listImprovements;
	private List<ChangelogEntry> listNewFeatures;

	private void clearLists() {
		listBugs = new ArrayList<ChangelogEntry>();
		listImprovements = new ArrayList<ChangelogEntry>();
		listNewFeatures = new ArrayList<ChangelogEntry>();
	}

	public Trac(String[] args) {
		parseParam(args);
		// Fix issues
		if ("C8oSDK".equals(product)) {
			this.tracQueryMilestonesURL = "http://devus.twinsoft.fr/report/37?format=tab";
		}
		
		clearLists();
	}

	protected String getHelp() {
		return "Usage:\n" + "-v=<current version number>\n" + "-p=<product name>\n"
				+ "-o=<output file path>\n";
	}

	private String product = null;
	private String currentVersion = null;
	private String outputFile = "changelog.txt";

	protected void parseParam(String[] args) {
		if (args.length != 3) {
			System.out.println(getHelp());
			System.exit(-1);
		}

		for (String arg : args) {
			if (arg.startsWith("-v=")) {
				currentVersion = arg.substring(3);
			} else if (arg.startsWith("-p=")) {
				product = arg.substring(3);
			} else if (arg.startsWith("-o=")) {
				outputFile = arg.substring(3);
			}
		}
		System.out.println("Creating changelog:");
		System.out.println("  * product: '" + product + "'");
		System.out.println("  * current version: " + currentVersion);
		System.out.println("  * output file: '" + outputFile + "'");
		System.out.println("");
	}

	private void computeChangelog() throws IOException {
		HttpClient httpclient = HttpClients.createDefault();
		HttpGet httpget;
		HttpResponse response;
		HttpEntity entity;

		// Get all milestones
		List<String> milestones = new ArrayList<String>();
		httpget = new HttpGet(tracQueryMilestonesURL);
		response = httpclient.execute(httpget);
		entity = response.getEntity();
		if (entity != null) {
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(entity.getContent()));
			String milestone = bufferedReader.readLine(); // Ignore the first line (titles)
			while ((milestone = bufferedReader.readLine()) != null) {
				if (VersionUtils.compare(milestone, currentVersion) <= 0) {
					milestones.add(milestone);
				}
			}
			
			Collections.sort(milestones, new Comparator<String>() {
				@Override
				public int compare(String o1, String o2) {
					return -1 * VersionUtils.compare(o1, o2);
				}
			});
		}

		Writer changelogWriter;

		if (outputFile.length() == 0) {
			changelogWriter = new PrintWriter(System.out);
		} else {
			changelogWriter = new FileWriter(new File(outputFile));
		}

		for (String milestone : milestones) {
			clearLists();

			String myTracQueryChangelogURL = tracQueryChangelogURL.replaceAll("%product%",
					product.replace(' ', '+'));
			myTracQueryChangelogURL = myTracQueryChangelogURL.replaceAll("%milestone%",
					milestone.replace(' ', '+'));

			System.out.println("Getting changelog for version '" + milestone + "'");
			System.out.println("TRAC changelog URL: '" + myTracQueryChangelogURL + "'");

			httpget = new HttpGet(myTracQueryChangelogURL);
			response = httpclient.execute(httpget);
			entity = response.getEntity();
			if (entity != null) {
				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(entity.getContent()));
				// Ignore the first line (titles)
				String line = bufferedReader.readLine();
				while ((line = bufferedReader.readLine()) != null) {
					try {
						parseLine(line);
					} catch (RuntimeException e) {
						System.err.println("line: " + line);
						//throw e;
					}
				}

				int nBugs = listBugs.size();
				int nImprovements = listImprovements.size();
				int nNewFeatures = listNewFeatures.size();

				System.out.println("   bugfixes: " + nBugs);
				System.out.println("   improvements: " + nImprovements);
				System.out.println("   new features: " + nNewFeatures);
				System.out.println("");

				if (nBugs + nImprovements + nNewFeatures > 0) {
					String version = " Version " + milestone + " ";
					String sUnderline = "";
					for (int i = 0; i < version.length(); i++) {
						sUnderline += '=';
					}
					changelogWriter.write(sUnderline + "\n");
					changelogWriter.write(version + "\n");
					changelogWriter.write(sUnderline + "\n\n");

					if (nNewFeatures > 0)
						writeChangelogEntries(changelogWriter, ChangelogEntryTypes.NEW_FEATURE);
					if (nImprovements > 0)
						writeChangelogEntries(changelogWriter, ChangelogEntryTypes.IMPROVEMENT);
					if (nBugs > 0)
						writeChangelogEntries(changelogWriter, ChangelogEntryTypes.BUG);

					changelogWriter.flush();
				}
			}
		}

		changelogWriter.close();
	}

	private void writeChangelogEntries(Writer writer, ChangelogEntryTypes type) throws IOException {
		List<ChangelogEntry> changelogEntries = getChangelogEntryList(type);
		int nbEntries = changelogEntries.size();
		writer.write(" " + type.toString().toUpperCase() + "S (" + nbEntries + ")\n");
		for (ChangelogEntry changelogEntry : changelogEntries) {
			writer.write("  * " + changelogEntry.comment);
			if (changelogEntry.supportCaseId.length() > 0 && !changelogEntry.supportCaseId.equals("--")) {
				writer.write(" [support case #" + changelogEntry.supportCaseId + "]");
			}
			writer.write("\n");
		}
		if (nbEntries != 0)
			writer.write("\n");
	}

	private void parseLine(String line) {
		int i, j;

		i = 0;
		j = line.indexOf('\t');
		int id = Integer.parseInt(line.substring(i, j));

		i = j + 1;
		j = line.indexOf('\t', i);
		String type = line.substring(i, j);

		i = j + 1;
		j = line.indexOf('\t', i);
		String comment = line.substring(i, j);

		if (comment.startsWith("\"") && comment.endsWith("\"")) {
			comment = comment.substring(1, comment.length() - 2);
			comment = comment.replaceAll("\"\"", "\"");
		}
		i = j + 1;
		String supportCaseId = (i > line.length() ? "" : line.substring(i));

		List<ChangelogEntry> changelogEntry = getChangelogEntryList(type);
		changelogEntry.add(new ChangelogEntry(id, comment, supportCaseId));
	}

	private List<ChangelogEntry> getChangelogEntryList(String type) {
		List<ChangelogEntry> changelogEntries = null;
		if (ChangelogEntryTypes.BUG.toString().equals(type))
			changelogEntries = listBugs;
		else if (ChangelogEntryTypes.IMPROVEMENT.toString().equals(type))
			changelogEntries = listImprovements;
		else if (ChangelogEntryTypes.NEW_FEATURE.toString().equals(type))
			changelogEntries = listNewFeatures;
		return changelogEntries;
	}

	private List<ChangelogEntry> getChangelogEntryList(ChangelogEntryTypes type) {
		return getChangelogEntryList(type.toString());
	}
}
