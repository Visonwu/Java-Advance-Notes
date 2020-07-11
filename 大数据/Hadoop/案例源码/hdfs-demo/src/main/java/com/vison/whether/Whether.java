package com.vison.whether;

import org.apache.hadoop.io.WritableComparable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Vison
 * @date 2020/7/7 11:46
 * @Version 1.0
 */
public class Whether  implements WritableComparable<Whether> {

    private Integer year;
    private Integer month;
    private Integer day;
    private Integer temp;

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

    public Integer getDay() {
        return day;
    }

    public void setDay(Integer day) {
        this.day = day;
    }

    public Integer getTemp() {
        return temp;
    }

    public void setTemp(Integer temp) {
        this.temp = temp;
    }

    @Override
    public int compareTo(Whether that) {
        // 按照日期正序比较
        int c1 = this.getYear() - that.getYear();
        if (c1==0){
            int c2 = this.getMonth() - that.getMonth();
            if (c2 == 0){
                return this.getDay() - that.getDay();
            }
            return c2;
        }
        return c1;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeInt(year);
        out.writeInt(month);
        out.writeInt(day);
        out.writeInt(temp);
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        this.year = in.readInt();
        this.month = in.readInt();
        this.day = in.readInt();
        this.temp = in.readInt();
    }
}
