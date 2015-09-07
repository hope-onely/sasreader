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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A reader object that reads .sas7bdat files.
 * 
 * @author Kasper Sørensen
 */
public class SasReader {

	private static final Logger logger = LoggerFactory
			.getLogger(SasReader.class);

	// Subheader 'signatures'
	private static final byte[] SUBH_ROWSIZE = IO.toBytes(0xf7, 0xf7, 0xf7,
			0xf7);
	private static final byte[] SUBH_COLSIZE = IO.toBytes(0xf6, 0xf6, 0xf6,
			0xf6);
	private static final byte[] SUBH_COLTEXT = IO.toBytes(0xFD, 0xFF, 0xFF,
			0xFF);
	private static final byte[] SUBH_COLATTR = IO.toBytes(0xFC, 0xFF, 0xFF,
			0xFF);
	private static final byte[] SUBH_COLNAME = IO.toBytes(0xFF, 0xFF, 0xFF,
			0xFF);
	private static final byte[] SUBH_COLLABS = IO.toBytes(0xFE, 0xFB, 0xFF,
			0xFF);

	/**
	 * Magic number
	 */
	private static final byte[] MAGIC = IO.toBytes(0x0, 0x0, 0x0, 0x0, 0x0,
			0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0xc2, 0xea, 0x81, 0x60, 0xb3,
			0x14, 0x11, 0xcf, 0xbd, 0x92, 0x8, 0x0, 0x9, 0xc7, 0x31, 0x8c,
			0x18, 0x1f, 0x10, 0x11);

	private final File _file;

	public SasReader(File file) {
		if (file == null) {
			throw new IllegalArgumentException("file cannot be null");
		}
		_file = file;
	}

	public File getFile() {
		return _file;
	}

	protected static boolean isMagicNumber(int[] data) {
		return isMagicNumber(IO.toBytes(data));
	}

	protected static boolean isMagicNumber(byte[] data) {
		return isIdentical(data, MAGIC);
	}

	private static boolean isIdentical(byte[] data, byte[] expected) {
		if (data == null) {
			return false;
		}
		final byte[] comparedBytes;
		if (data.length > expected.length) {
			comparedBytes = Arrays.copyOf(data, expected.length);
		} else {
			comparedBytes = data;
		}
		return Arrays.equals(expected, comparedBytes);
	}

	public void read(SasReaderCallback callback) throws SasReaderException {
		FileInputStream is = null;
		try {
			is = new FileInputStream(_file);

			SasHeader header = readHeader(is);
			logger.info("({}) Header: {}", _file, header);

			readPages(is, header, callback);

			logger.info("({}) Done!", _file);
		} catch (Exception e) {
			if (e instanceof SasReaderException) {
				throw (SasReaderException) e;
			}
			throw new SasReaderException(
					"Unhandled exception occurred while reading sas7bdat file!",
					e);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					// do nothing
				}
			}
		}
	}

	private void readPages(FileInputStream is, SasHeader header,
			SasReaderCallback callback) throws Exception {
		final List<SasSubHeader> subHeaders = new ArrayList<SasSubHeader>();
		final List<Integer> columnOffsets = new ArrayList<Integer>();
		final List<Integer> columnLengths = new ArrayList<Integer>();
		final List<SasColumnType> columnTypes = new ArrayList<SasColumnType>();
		boolean subHeadersParsed = false;

		int rowCount = 0;

		final int pageSize = header.getPageSize();
		final int pageCount = header.getPageCount();

		// these variables will define the default amount of rows per page and
		// other defaults
		int row_count = -1;
		int row_count_fp = -1;
		int row_length = -1;
		int col_count = -1;

		for (int pageNumber = 0; pageNumber < pageCount; pageNumber++) {
			logger.info("({}) Reading page no. {}", _file, pageNumber);
			final byte[] pageData = new byte[pageSize];
			int read = is.read(pageData);
			if (read == -1) {
				// reached end of file
				break;
			}

			boolean u64 = header.isU64();
			byte pageType = IO.readByte(pageData, u64 ? 33 : 17);

			switch (pageType) {
			case 0:
			case 1:
			case 2:
				// accepted type
				logger.info("({}) page type supported: {}", _file, pageType);
				break;
			case 4:
				// accepted but not supported
				logger.info("({}) page type not fully supported: {}", _file,
						pageType);
				break;
			default:
				throw new SasReaderException("Page " + pageNumber
						+ " has unknown type: " + pageType);
			}

			if (pageType == 0 || pageType == 2) {
				// Read subheaders
				int subhCount = IO.readInt(pageData, u64 ? 36 : 20);
				 //page offset of subheader pointers
				    int oshp= u64 ? 40 : 24;
				 //length of subheader pointers
				   int lshp=  u64 ? 24 : 12;
				   //length of first two subheader fields
				    int lshf=  u64 ? 8  : 4;
				for (int subHeaderNumber = 0; subHeaderNumber < subhCount; subHeaderNumber++) {
					int base = oshp + subHeaderNumber * lshp;

					int offset = IO.readNumber2(pageData, base,lshf).intValue();
					int length = IO.readNumber2(pageData, base + lshf,lshf).intValue();

					if (length > 0) {
						byte[] rawData = IO.readBytes(pageData, offset, length);
						byte[] signatureData = IO.readBytes(rawData, 0, 4);
						SasSubHeader subHeader = new SasSubHeader(rawData,
								signatureData);
						subHeaders.add(subHeader);
					}
				}
			}

			if ((pageType == 1 || pageType == 2)) {

				if (!subHeadersParsed) {
					// Parse subheaders
					int offp =  u64 ? 8  : 4;
					SasSubHeader rowSize = getSubHeader(subHeaders,
							SUBH_ROWSIZE, "ROWSIZE");
					row_length = IO.readNumber2(rowSize.getRawData(), u64?40 : 20,offp).intValue();
					row_count = IO.readNumber2(rowSize.getRawData(), u64? 48 : 24,offp).intValue();
					int col_count_7 = IO.readNumber2(rowSize.getRawData(), u64? 72 : 36,offp).intValue();
					row_count_fp = IO.readNumber2(rowSize.getRawData(), u64?120 : 60,offp).intValue();

					SasSubHeader colSize = getSubHeader(subHeaders,
							SUBH_COLSIZE, "COLSIZE");
					int col_count_6 = IO.readNumber2(colSize.getRawData(), u64? 8 : 4,offp).intValue();
					col_count = col_count_6;

					if (col_count_7 != col_count_6) {
						logger.warn(
								"({}) Column count mismatch: {} vs. {}",
								new Object[] { _file, col_count_6, col_count_7 });
					}

					SasSubHeader colText = getSubHeader(subHeaders,
							SUBH_COLTEXT, "COLTEXT");

					List<SasSubHeader> colAttrHeaders = getSubHeaders(
							subHeaders, SUBH_COLATTR, "COLATTR");
					final SasSubHeader colAttr;
					if (colAttrHeaders.isEmpty()) {
						throw new SasReaderException(
								"No column attribute subheader found");
					} else if (colAttrHeaders.size() == 1) {
						colAttr = colAttrHeaders.get(0);
					} else {
						colAttr = spliceColAttrSubHeaders(colAttrHeaders);
					}

					SasSubHeader colName = getSubHeader(subHeaders,
							SUBH_COLNAME, "COLNAME");

					List<SasSubHeader> colLabels = getSubHeaders(subHeaders,
							SUBH_COLLABS, "COLLABS");
					if (!colLabels.isEmpty() && colLabels.size() != col_count) {
						throw new SasReaderException(
								"Unexpected column label count ("
										+ colLabels.size() + ") expected 0 or "
										+ col_count);
					}

					for (int i = 0; i < col_count; i++) {
						int base = u64 ? 16  : 12 + i * 8;

						final String columnName;
						byte amd = IO.readByte(colName.getRawData(), base);
						if (amd == 0) {
							int off = IO.readShort(colName.getRawData(),
									base + 2) + 4;
							int len = IO.readShort(colName.getRawData(),
									base + 4);
							columnName = IO.readString(colText.getRawData(),
									off, len);
						} else {
							columnName = "COL" + i;
						}

						// Read column labels
						final String label;
						String format = null;
						if (colLabels != null && !colLabels.isEmpty()) {
							base = u64?52 : 40;
							byte[] rawData = colLabels.get(i).getRawData();
							int off = IO.readShort(rawData, base+2);
							short len = IO.readShort(rawData, base + 4);
							if (len > 0) {
								label = IO.readString(colText.getRawData(),
										off+ offp, len);
							} else {
								label = null;
							}
							
							base = u64? 46 : 34;
					        off = IO.readShort(rawData, base + 2);
					        len = IO.readShort(rawData, base + 4);
					        if(len > 0)
					        	format =  IO.readString(colText.getRawData(),
	                                    off + offp, len);
						} else {
							label = null;
						}
						
						
						
						int lcav = u64? 16 : 12;
						// Read column offset, width, type (required)
						base = lcav + i * lcav;

						int offset =IO.readNumber2(colAttr.getRawData(), base,u64?8:4).intValue();
						columnOffsets.add(offset);

						int length =  IO.readNumber2(colAttr.getRawData(), base + 4,u64?8:4).intValue();
						columnLengths.add(length);

						short columnTypeCode = IO.readShort(
								colAttr.getRawData(), base + (u64? 14: 10));
						SasColumnType columnType = (columnTypeCode == 1 ? SasColumnType.NUMERIC
								: SasColumnType.CHARACTER);
						columnTypes.add(columnType);

							logger.info(
									"({}) column no. {} read: name={},label={},type={},format={},length={}",
									new Object[] { _file, i, columnName, label,
											columnType,format, length });
						callback.column(i, columnName, label, columnType,format,
								length);
					}

					subHeadersParsed = true;
				}

				if (!callback.readData()) {
					logger.info("({}) Callback decided to not read data", _file);
					return;
				}

				// Read data
				int row_count_p;
				int base =(u64? 32 : 16) + 8;
				if (pageType == 2) {
					row_count_p = row_count_fp;
					int subhCount = IO.readInt(pageData, 20);
					base = base  + subhCount * (u64? 24 : 12);
					base = base + base % 8;
				} else {
					row_count_p = IO.readInt(pageData, u64? 34 : 18);
				}
				base= ((base+7) / 8) * 8 + base % 8;
				
				if (row_count_p > row_count) {
					row_count_p = row_count;
				}

				for (int row = 0; row < row_count_p; row++) {
					Object[] rowData = new Object[col_count];
					for (int col = 0; col < col_count; col++) {
						int off = base + columnOffsets.get(col);
						int len = columnLengths.get(col);

						SasColumnType columnType = columnTypes.get(col);
						if (len > 0) {
							byte[] raw = IO.readBytes(pageData, off, len);
							if (columnType == SasColumnType.NUMERIC && len < 8) {
								ByteBuffer bb = ByteBuffer.allocate(8);
								for (int j = 0; j < 8 - len; j++) {
									bb.put((byte) 0x00);
								}
								bb.put(raw);
								raw = bb.array();

								// col$length <- 8
								len = 8;
							}

							final Object value;
							if (columnType == SasColumnType.CHARACTER) {
								String str = IO.readString(raw, 0, len);
								str = str.trim();
								value = str;
							} else {
								value = IO.readNumber(raw, 0, len);
							}
							rowData[col] = value;
						}
					}

					if (logger.isDebugEnabled()) {
						logger.debug("({}) row no. {} read: {}", new Object[] {
								_file, row, rowData });
					}

					rowCount++;
					boolean next = callback.row(rowCount, rowData);
					if (!next) {
						logger.info("({}) Callback decided to stop iteration",
								_file);
						return;
					}

					base = base + row_length;
				}
			}
		}
	}

	private SasSubHeader spliceColAttrSubHeaders(
			List<SasSubHeader> colAttrHeaders) {
		final int colAttrHeadersSize = colAttrHeaders.size();
		logger.info("({}) Splicing {} column attribute headers", _file,
				colAttrHeadersSize);

		byte[] result = IO.readBytes(colAttrHeaders.get(0).getRawData(), 0,
				colAttrHeaders.get(0).getRawData().length - 8);

		for (int i = 1; i < colAttrHeadersSize; i++) {
			byte[] rawData = colAttrHeaders.get(i).getRawData();
			result = IO.concat(result,
					IO.readBytes(rawData, 12, rawData.length - 20));
		}

		return new SasSubHeader(result, null);
	}

	private List<SasSubHeader> getSubHeaders(List<SasSubHeader> subHeaders,
			byte[] signature, String name) {
		List<SasSubHeader> result = new ArrayList<SasSubHeader>();
		for (SasSubHeader subHeader : subHeaders) {
			byte[] signatureData = subHeader.getSignatureData();
			if (isIdentical(signatureData, signature)) {
				result.add(subHeader);
			}
		}
		return result;
	}

	private SasSubHeader getSubHeader(List<SasSubHeader> subHeaders,
			byte[] signature, final String name) {
		List<SasSubHeader> result = getSubHeaders(subHeaders, signature, name);
		if (result.isEmpty()) {
			throw new SasReaderException("Could not find sub header: " + name);
		} else if (result.size() != 1) {
			throw new SasReaderException("Multiple (" + result.size()
					+ ") instances of the same sub header: " + name);
		}
		return result.get(0);
	}

	private SasHeader readHeader(InputStream is) throws Exception {
		byte[] header = new byte[288];
		int read = is.read(header);
		if (read != 288) {
			throw new SasReaderException(
					"Header too short (not a sas7bdat file?): " + read);
		}

		if (!isMagicNumber(header)) {
			throw new SasReaderException("Magic number mismatch!");
		}

		// Check for 32 or 64 bit alignment
		int align1 = 0;
		byte[] osbyte = IO.readBytes(header, 32, 1);
		if (isIdentical(osbyte, IO.toBytes(0x33))) {
			align1 = 4;
		}
		boolean u64 = false;
		// If align1 == 4, file is u64 type
		if (align1 == 4) {
			u64 = true;
		}
		int align2 = 0;
		byte[] align2byte = IO.readBytes(header, 35, 1);
		if (isIdentical(align2byte, IO.toBytes(0x33))) {
			align2 = 4;
		}
		String endian = "";
		byte[] endianbyte = IO.readBytes(header, 37, 1);
		if (isIdentical(endianbyte, IO.toBytes(0x01))) {
			endian = "little";
		} else {
			endian = "big";
			throw new SasReaderException("big endian files are not supported");
		}
		
		String winunix = IO.readString(header, 39, 1);
	    if(winunix.equals("1")) {
	        winunix="unix";
	    } else if(winunix.equals("2")) {
	        winunix="windows";
	    } else {
	        winunix="unknown";
	    }
	    int header_length = IO.readInt(header, 196 + align2);
	    byte[] leftHeadByte = new byte[header_length-288]; 
	    read = is.read(leftHeadByte);
	    byte[] total = new byte[header_length];
	    System.arraycopy(header, 0, total, 0, 288);
	    System.arraycopy(leftHeadByte, 0, total, 288, header_length-288);
		final int pageSize = IO.readInt(header, 200 + align2);
		if (pageSize < 0) {
			throw new SasReaderException("Page size is negative: " + pageSize);
		}

		final int pageCount = IO.readInt(header, 204 + align2);
		if (pageCount < 1) {
			throw new SasReaderException("Page count is not positive: "
					+ pageCount);
		}

		String SAS_release=IO.readString(total, 216 + align1 + align2, 8);
		//SAS_host is a 16 byte field, but only the first eight are used
	    //FIXME: It would be preferable to eliminate this check
		String SAS_host=IO.readString(total, 224 + align1 + align2, 8);

	    String OS_version=IO.readString(total, 240 + align1 + align2, 16) ;
	    String OS_maker=IO.readString(total, 256 + align1 + align2, 16) ;
	    String OS_name=IO.readString(total, 272+ align1 + align2, 16) ;
	    
		logger.info("({}) page size={}, page count={}", new Object[] { _file,
				pageSize, pageCount });

		final String sasRelease = IO.readString(header, 216, 8);
		final String sasHost = IO.readString(header, 224, 8);
		SasHeader sasheader= new SasHeader(sasRelease, sasHost, pageSize, pageCount);
		sasheader.setAlign1(align1);
		sasheader.setAlign2(align2);
		sasheader.setU64(u64);
		sasheader.setEndian(endian);
		sasheader.setSAS_host(SAS_host);
		sasheader.setOS_maker(OS_maker);
		sasheader.setOS_name(OS_name);
		sasheader.setOS_version(OS_version);
		sasheader.setSAS_release(SAS_release);
		return sasheader;
	}
}
