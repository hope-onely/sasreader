/**
 * eobjects.org SassyReader
 * Copyright (C) 2011 eobjects.org
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.eobjects.sassy;

/**
 * Represents the header metadata in the sas7bdat file format.
 * 
 * @author Kasper Sørensen
 */
final class SasHeader {

	private final String sasRelease;
	private final String sasHost;
	private final int pageSize;
	private final int pageCount;
	/**
	 * 是否是64位操作系统
	 */
	private boolean u64;
	/**
	 * 偏移位数，64位是4，32位是0
	 */
	private int align1;
	
	private int align2;
	
	private String endian;
	
	private String winunix;
	
	private String datecreated;
	
	private String datemodified;
	
	private String SAS_release;
	
	private String SAS_host;
	
	private String OS_version;
	
	private String OS_maker;
	
	private String OS_name;

	public SasHeader(String sasRelease, String sasHost, int pageSize,
			int pageCount) {
		this.sasRelease = sasRelease;
		this.sasHost = sasHost;
		this.pageSize = pageSize;
		this.pageCount = pageCount;
	}

	public String getSasRelease() {
		return sasRelease;
	}

	public String getSasHost() {
		return sasHost;
	}

	public int getPageSize() {
		return pageSize;
	}

	public int getPageCount() {
		return pageCount;
	}

	@Override
	public String toString() {
		return "SasHeader [sasRelease=" + sasRelease + ", sasHost=" + sasHost
				+ ", pageSize=" + pageSize + ", pageCount=" + pageCount
				+ ", u64=" + u64 + ", align1=" + align1 + ", align2=" + align2
				+ ", endian=" + endian + ", winunix=" + winunix
				+ ", datecreated=" + datecreated + ", datemodified="
				+ datemodified + ", SAS_release=" + SAS_release + ", SAS_host="
				+ SAS_host + ", OS_version=" + OS_version + ", OS_maker="
				+ OS_maker + ", OS_name=" + OS_name + "]";
	}

	public boolean isU64() {
		return u64;
	}

	public void setU64(boolean u64) {
		this.u64 = u64;
	}

	public int getAlign1() {
		return align1;
	}

	public void setAlign1(int align1) {
		this.align1 = align1;
	}

	public int getAlign2() {
		return align2;
	}

	public void setAlign2(int align2) {
		this.align2 = align2;
	}

	public String getEndian() {
		return endian;
	}

	public void setEndian(String endian) {
		this.endian = endian;
	}

	public String getWinunix() {
		return winunix;
	}

	public void setWinunix(String winunix) {
		this.winunix = winunix;
	}

	public String getDatecreated() {
		return datecreated;
	}

	public void setDatecreated(String datecreated) {
		this.datecreated = datecreated;
	}

	public String getDatemodified() {
		return datemodified;
	}

	public void setDatemodified(String datemodified) {
		this.datemodified = datemodified;
	}

	public String getSAS_release() {
		return SAS_release;
	}

	public void setSAS_release(String sAS_release) {
		SAS_release = sAS_release;
	}

	public String getSAS_host() {
		return SAS_host;
	}

	public void setSAS_host(String sAS_host) {
		SAS_host = sAS_host;
	}

	public String getOS_version() {
		return OS_version;
	}

	public void setOS_version(String oS_version) {
		OS_version = oS_version;
	}

	public String getOS_maker() {
		return OS_maker;
	}

	public void setOS_maker(String oS_maker) {
		OS_maker = oS_maker;
	}

	public String getOS_name() {
		return OS_name;
	}

	public void setOS_name(String oS_name) {
		OS_name = oS_name;
	}
	
	
}
