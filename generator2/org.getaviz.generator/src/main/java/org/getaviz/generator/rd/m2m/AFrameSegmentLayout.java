package org.getaviz.generator.rd.m2m;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getaviz.generator.rd.DiskSegment;
import org.getaviz.generator.rd.SubDisk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class AFrameSegmentLayout {

    private final List<SubDisk> disks;
    private final Log log = LogFactory.getLog(this.getClass());
    AFrameSegmentLayout(List<SubDisk> disks) {
        this.disks = disks;
    }

    void calculateOuterSegments() {
        disks.forEach(disk -> {
            double r_data = disk.getInnerSegmentsRadius();
            double r_methods;
            double radius = disk.getRadius();
            double ringWidth = disk.getBorderWidth();
            ArrayList<DiskSegment> outerSegments = disk.getOuterSegments();
            double outerSegmentsArea  = disk.getOuterSegmentsArea();
            sortSegmentsBySize(outerSegments);
            if (disk.hasInnerDisks()) {
                r_methods = Math.sqrt((outerSegmentsArea / Math.PI) + (r_data * r_data));
            } else {
                r_methods = radius - ringWidth;
            }
            if (!outerSegments.isEmpty()) {
                calculateAngle(outerSegments, outerSegmentsArea, r_methods, r_data);
            }
        });
    }

    void calculateInnerSegments() {
        disks.forEach(disk -> {
            double innerRadius = disk.getInnerRadius();
            ArrayList<DiskSegment> innerSegments = disk.getInnerSegments();
            sortSegmentsByFQN(innerSegments);
            double innerSegmentsArea = disk.getInnerSegmentsArea();
            double r_data = Math.sqrt((innerSegmentsArea/ Math.PI) + (innerRadius * innerRadius));
            disk.setInnerSegmentsRadius(r_data);
            if (!innerSegments.isEmpty()) {
                calculateAngle(innerSegments, innerSegmentsArea, r_data, innerRadius);
            }
        });
    }

    private void calculateAngle(ArrayList<DiskSegment> segments, double sizeSum, double outer, double inner) {
        if (!segments.isEmpty()) {
            double position = 90.0;
            for (DiskSegment segment : segments) {
                double angle = (segment.getSize() / sizeSum) * 360 - 1;
                segment.setAnglePosition(position);
                if(angle < 0.1) {
                    angle = 0.1;
                    position += angle + 0.9;
                } else {
                    position += angle + 1;
                }
                segment.setAngle(angle);
                if (position >= 360) {
                    position -= 360;
                }
                segment.setOuterRadius(outer);
                segment.setInnerRadius(inner);
            }
        }
    }

    private void sortSegmentsByFQN(ArrayList<DiskSegment> innerSegments) {
        innerSegments.sort(Comparator.comparing(DiskSegment::getFqn));
        Collections.reverse(innerSegments);
    }

    private void sortSegmentsBySize(ArrayList<DiskSegment> outerSegments) {
        outerSegments.sort(Comparator.comparing(DiskSegment::getSize));
    }
}
