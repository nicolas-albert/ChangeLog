/*
 * Copyright (c) 2001-2011 Convertigo SA.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 *
 * $URL: http://sourceus/svn/convertigo/CEMS_opensource/branches/6.2.x/Studio/src/com/twinsoft/convertigo/engine/util/VersionUtils.java $
 * $Author: nicolasa $
 * $Revision: 30856 $
 * $Date: 2012-06-15 14:33:51 +0200 (ven., 15 juin 2012) $
 */

package com.convertigo.changelog;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

public class VersionUtils {
	private static Map<String, String> normalizedVersions = new WeakHashMap<String, String>();
    
    public static int compareProductVersion(String version1, String version2) {
		version1 = version1.substring(0, version1.indexOf('.', version1.indexOf('.') + 1));
		version2 = version2.substring(0, version2.indexOf('.', version2.indexOf('.') + 1));
		
		return VersionUtils.compare(version1, version2);
	}
    
	public static int compare(String v1, String v2) {
		String s1 = normalizeVersionString(v1);
		String s2 = normalizeVersionString(v2);
		int cmp = s1.compareTo(s2);
		return cmp;
	}

	public synchronized static String normalizeVersionString(String version) {
		String normalizedVersion = normalizedVersions.get(version);
		if (normalizedVersion == null) {
			normalizedVersions.put(version, normalizedVersion = VersionUtils.normalizeVersionString(version, ".", 4));
		}
		return normalizedVersion;
	}

	public static String normalizeVersionString(String version, String sep, int maxWidth) {
		String[] split = Pattern.compile(sep, Pattern.LITERAL).split(version);
		StringBuilder sb = new StringBuilder();
		for (String s : split) {
			sb.append(String.format("%" + maxWidth + 's', s));
		}
		return sb.toString();
	}

	public static int compareMigrationVersion(String v1, String v2) {
		int i1 = v1.indexOf(".m");
		if (i1 == -1) v1 = "000";
		else v1 = v1.substring(i1 + 2);

		int i2 = v2.indexOf(".m");
		if (i2 == -1) v2 = "000";
		else v2 = v2.substring(i2 +2);

		int cmp = v1.compareTo(v2);
		return cmp;
	}

}
