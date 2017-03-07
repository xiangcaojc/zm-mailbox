/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2016, 2017 Synacor, Inc.
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
package com.zimbra.cs.filter.jsieve;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

import org.apache.jsieve.Arguments;
import org.apache.jsieve.Block;
import org.apache.jsieve.SieveContext;
import org.apache.jsieve.commands.AbstractCommand;
import org.apache.jsieve.exception.OperationException;
import org.apache.jsieve.exception.SieveException;
import org.apache.jsieve.exception.SyntaxException;
import org.apache.jsieve.mail.MailAdapter;

import com.zimbra.common.util.CharsetUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.filter.FilterUtil;
import com.zimbra.cs.filter.ZimbraMailAdapter;

public class ReplaceHeader extends AbstractCommand {
    private EditHeaderExtension ehe = new EditHeaderExtension();

    /** (non-Javadoc)
     * @see org.apache.jsieve.commands.AbstractCommand#executeBasic(org.apache.jsieve.mail.MailAdapter, org.apache.jsieve.Arguments, org.apache.jsieve.Block, org.apache.jsieve.SieveContext)
     */
    @SuppressWarnings("unchecked")
    @Override
    protected Object executeBasic(MailAdapter mail, Arguments arguments,
            Block block, SieveContext sieveContext) throws SieveException {
        if (!(mail instanceof ZimbraMailAdapter)) {
            ZimbraLog.filter.info("replaceheader: Zimbra mail adapter not found.");
            return null;
        }

        // make sure zcs do not edit immutable header
        if (ehe.isImmutableHeaderKey()) {
            ZimbraLog.filter.info("replaceheader: %s is immutable header, so exiting silently.", ehe.getKey());
            return null;
        }
        ZimbraMailAdapter mailAdapter = (ZimbraMailAdapter) mail;
        // replace sieve variables
        ehe.replaceVariablesInValueList(mailAdapter);
        MimeMessage mm = mailAdapter.getMimeMessage();
        Enumeration<Header> headers;
        try {
            headers = mm.getAllHeaders();
            if (!headers.hasMoreElements()) {
                ZimbraLog.filter.info("replaceheader: No headers found in mime.");
                return null;
            }
        } catch (MessagingException e) {
            throw new OperationException("replaceheader: Error occured while fetching all headers from mime.", e);
        }
        int headerCount = ehe.getHeaderCount(mm);
        if (headerCount < 1) {
            ZimbraLog.filter.info("replaceheader: No headers found matching with \"%s\" in mime.", ehe.getKey());
            return null;
        }
        ehe.setEffectiveIndex(headerCount);
        int matchIndex = 0;
        List<Header> newHeaderList = new ArrayList<Header>();
        try {
            while (headers.hasMoreElements()) {
                Header header = headers.nextElement();
                String newHeaderName = null;
                String newHeaderValue = null;
                boolean replace = false;
                if (header.getName().equalsIgnoreCase(ehe.getKey())){
                    matchIndex++;
                    if (ehe.getIndex() == null || (ehe.getIndex() != null && ehe.getIndex() == matchIndex)) {
                        ZimbraLog.filter.debug("replaceheader: header before processing\n%d  %s: %s", matchIndex, header.getName(), header.getValue());
                        for (String value : ehe.getValueList()) {
                            ZimbraLog.filter.debug("replaceheader: working with %s value", value);
                            replace = ehe.matchCondition(mailAdapter, header, headerCount, value, sieveContext);
                            if (replace) {
                                if (ehe.getNewName() != null) {
                                    newHeaderName = ehe.getNewName();
                                } else {
                                    newHeaderName = header.getName();
                                }
                                if (ehe.getNewValue() != null) {
                                    newHeaderValue = FilterUtil.replaceVariables(mailAdapter, ehe.getNewValue());
                                    newHeaderValue = MimeUtility.fold(newHeaderName.length() + 2, MimeUtility.encodeText(newHeaderValue));
                                } else {
                                    newHeaderValue = header.getValue();
                                }
                                ZimbraLog.filter.debug("replaceheader: header after processing\n%s: %s", newHeaderName, newHeaderValue);
                                header = new Header(newHeaderName, newHeaderValue);
                                break;
                            }
                        }
                    }
                }
                newHeaderList.add(header);
             }
            // now remove all headers from mime and add from new list
            headers = mm.getAllHeaders();
            while (headers.hasMoreElements()) {
                Header header = headers.nextElement();
                mm.removeHeader(header.getName());
            }
            for (Header header : newHeaderList) {
                mm.addHeaderLine(header.getName() + ": " + header.getValue());
            }
            mm.saveChanges();
            mailAdapter.updateIncomingBlob();
        } catch (MessagingException me) {
            throw new OperationException("replaceheader: Error occured while operating mime.", me);
        } catch (UnsupportedEncodingException uee) {
            throw new OperationException("replaceheader: Error occured while encoding header value.", uee);
        }
        return null;
    }

    /** (non-Javadoc)
     * @see org.apache.jsieve.commands.AbstractCommand#validateArguments(org.apache.jsieve.Arguments, org.apache.jsieve.SieveContext)
     */
    @Override
    protected void validateArguments(Arguments arguments, SieveContext context)
            throws SieveException {
        ZimbraLog.filter.debug("replaceheader: %s", arguments.getArgumentList().toString());
        ehe.setupEditHeaderData(arguments, this);
        // Match type or Comparator type condition must be present
        if (!(ehe.isIs() || ehe.isContains() || ehe.isMatches() || ehe.isCountTag() || ehe.isValueTag())) {
            throw new SyntaxException("replaceheader: Match type or Comparator type must be present in replaceheader.");
        }

        // Key and value both must be present at a time
        if (ehe.getKey() == null || ehe.getValueList() == null) {
            throw new SyntaxException("replaceheader: key or value not found in replaceheader.");
        }
        ZimbraLog.filter.debug("replaceheader: header key in sieve script = %s", ehe.getKey());
        ZimbraLog.filter.debug("replaceheader: header values in sieve script = %s", Arrays.toString(ehe.getValueList().toArray()));

        // character set validation
        if (ehe.getNewName() != null) {
            ZimbraLog.filter.debug("replaceheader: new header name in sieve script = %s", ehe.getNewName());
            if (!CharsetUtil.US_ASCII.equals(CharsetUtil.checkCharset(ehe.getNewName(), CharsetUtil.US_ASCII))) {
                throw new SyntaxException("replaceheader: newname must be printable ASCII only in replaceheader.");
            }
        }
        if (ehe.getNewValue() != null) {
            ZimbraLog.filter.debug("replaceheader: new header vlaue in sieve script = %s", ehe.getNewValue());
        }
        ehe.commonValidation();
    }
}