/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2019 Synacor, Inc.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.html.owasp.policies;

import java.util.List;
import java.util.regex.Pattern;

import org.owasp.html.ElementPolicy;

import com.zimbra.common.localconfig.DebugConfig;

public class ImgInputElementPolicy implements ElementPolicy {

    private static final Pattern VALID_IMG_FILE = Pattern.compile(DebugConfig.defangValidImgFile);
    private static final Pattern VALID_INT_IMG = Pattern.compile(DebugConfig.defangValidIntImg,
        Pattern.CASE_INSENSITIVE);
    private static final Pattern VALID_EXT_URL = Pattern.compile(DebugConfig.defangValidExtUrl, Pattern.CASE_INSENSITIVE);
    // matches the file format that convertd uses so it doesn't get removed
    private static final Pattern VALID_CONVERTD_FILE = Pattern
        .compile(DebugConfig.defangValidConvertdFile);

    @Override
    public String apply(String elementName, List<String> attrs) {
        final int srcIndex = attrs.indexOf("src");
        if (srcIndex == -1) {
            return elementName;
        }
        String srcValue = attrs.get(srcIndex + 1);
        if (VALID_EXT_URL.matcher(srcValue).find()
                || (!VALID_INT_IMG.matcher(srcValue).find() && !VALID_IMG_FILE.matcher(srcValue).find())) {
            attrs.remove(srcIndex);
            attrs.remove(srcIndex);// value
            attrs.add("dfsrc");
            attrs.add(srcValue);
        } else if (!VALID_INT_IMG.matcher(srcValue).find() && VALID_IMG_FILE.matcher(srcValue).find()
                && !VALID_CONVERTD_FILE.matcher(srcValue).find()) {
            attrs.remove(srcIndex);
            attrs.remove(srcIndex);// value
            attrs.add("pnsrc");
            attrs.add(srcValue);
        }
        return elementName;
    }

}
