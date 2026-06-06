package com.danmakulive.video.model.dto;

public class DensityDTO {

    private int segment;
    private int count;

    public DensityDTO() {}

    public DensityDTO(int segment, int count) {
        this.segment = segment;
        this.count = count;
    }

    public int getSegment() { return segment; }
    public void setSegment(int segment) { this.segment = segment; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
