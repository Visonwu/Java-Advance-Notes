使用`poi`完成Excel 文件解析和生成

```java
public class ExcelUtils {

    private static final String EXCEL_XLS = "xls";
    private static final String EXCEL_XLSX = "xlsx";

    /**
     * 判断Excel的版本,获取Workbook
     *
     * @param in
     * @param file
     * @return
     * @throws IOException
     */
    public static Workbook getWorkbok(InputStream in, File file) throws IOException {
        Workbook wb = null;
        if (file.getName().endsWith(EXCEL_XLS)) {  //Excel 2003
            wb = new HSSFWorkbook(in);
        } else if (file.getName().endsWith(EXCEL_XLSX)) {  // Excel 2007/2010
            wb = new XSSFWorkbook(in);
        }
        return wb;
    }

    /**
     * 判断文件是否是excel
     *
     * @throws Exception
     */
    public static void checkExcelVaild(File file) throws Exception {
        if (!file.exists()) {
            throw new Exception("文件不存在");
        }
        if (!(file.isFile() && (file.getName().endsWith(EXCEL_XLS) || file.getName().endsWith(EXCEL_XLSX)))) {
            throw new Exception("文件不是Excel");
        }
    }

    /**
     * 根据一行为一组数据 返回所有cell的值 第一组数据为属性标题
     * key 为sheetName, value为行 值，内嵌list为每行中的单元值
     * @return 封装成Map<String ,List<List<String>>>
     */
    public static Map<String ,List<List<String>>> getValueList(File file) throws Exception {

        FileInputStream in = new FileInputStream(file); // 文件流
        checkExcelVaild(file);
        Workbook workbook = getWorkbok(in, file);
        int numberOfSheets = workbook.getNumberOfSheets();
        Map<String ,List<List<String>>> map = Maps.newHashMap();
        for(int i =0;i <numberOfSheets;i++){
            Sheet sheet = workbook.getSheetAt(i);
            List<List<String>> valuesBySheet = getValuesBySheet(sheet);
            map.put(sheet.getSheetName(),valuesBySheet);
        }
        return  map;
    }

    private static List<List<String>> getValuesBySheet(Sheet sheet){
        List<List<String>> values = new ArrayList<List<String>>();
        for (Row row : sheet) {
            List<String> modelValue = new ArrayList<>();
            //如果当前行没有数据，跳出循环
            if (row.getCell(0).toString().equals("")) {
                continue;
            }
            int end = row.getLastCellNum();
            for (int i = 0; i < end; i++) {
                Cell cell = row.getCell(i);
                if (cell == null) {
                    modelValue.add(null);
                    continue;
                }
                Object obj = getValue(cell);
                modelValue.add(obj.toString());
            }
            values.add(modelValue);
        }
        return values;
    }


    private static Object getValue(Cell cell) {
        Object obj = null;
        switch (cell.getCellTypeEnum()) {
            case BOOLEAN:
                obj = cell.getBooleanCellValue();
                break;
            case ERROR:
                obj = cell.getErrorCellValue();
                break;
            case NUMERIC:
                obj = cell.getNumericCellValue();
                break;
            case STRING:
                obj = cell.getStringCellValue();
                break;
            default:
                break;
        }
        return obj;
    }


    public static HSSFCellStyle createHeaderStyle(HSSFWorkbook wb, short fontSize) {
        HSSFCellStyle headerStyle = wb.createCellStyle();
        headerStyle.setAlignment(HSSFCellStyle.ALIGN_CENTER);  //居中
        headerStyle.setBorderBottom(BorderStyle.MEDIUM);
        headerStyle.setBorderLeft(BorderStyle.MEDIUM);
        headerStyle.setBorderRight(BorderStyle.MEDIUM);
        headerStyle.setBorderTop(BorderStyle.MEDIUM);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        //生成一个字体
        HSSFFont font = wb.createFont();
        font.setColor(HSSFColor.BLACK.index);
        font.setFontHeightInPoints(fontSize);
        font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);         //字体增粗
        headerStyle.setFont(font);
        return headerStyle;
    }

    public static HSSFCellStyle createDataStyle(HSSFWorkbook wb, short fontSize) {
        HSSFCellStyle dataStyle = wb.createCellStyle();
        dataStyle.setBorderBottom(BorderStyle.MEDIUM);
        dataStyle.setBorderLeft(BorderStyle.MEDIUM);
        dataStyle.setBorderRight(BorderStyle.MEDIUM);
        dataStyle.setBorderTop(BorderStyle.MEDIUM);
        //生成一个字体
        HSSFFont dataFont = wb.createFont();
        dataFont.setColor(HSSFColor.BLACK.index);
        dataFont.setFontHeightInPoints(fontSize);
        dataStyle.setFont(dataFont);
        return dataStyle;
    }

    public static void createHeaders(HSSFRow row, String[] headerNames, HSSFCellStyle headerStyle) {
        for (int i = 0; i < headerNames.length; i++) {
            //第四步创建单元格
            HSSFCell cell = row.createCell(i); //第一个单元格
            cell.setCellStyle(headerStyle);
            cell.setCellValue(headerNames[i]);
        }
    }
}
```

