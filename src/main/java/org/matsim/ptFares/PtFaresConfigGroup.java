package org.matsim.ptFares;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author dwedekind
 */

public class PtFaresConfigGroup extends ReflectiveConfigGroup {

    public static final String GROUP_NAME = "ptFares";
    private static final String PT_FARE_ZONE_ATTRIBUTE_NAME = "ptFareZoneAttributeName";

    private static String ptFareZoneAttributeName = "ptFareZone";
    private ZonesGroup zonesGroup = new ZonesGroup();
    private FaresGroup faresGroup = new FaresGroup();


    public PtFaresConfigGroup() {
        super(GROUP_NAME);
    }


    @StringGetter(PT_FARE_ZONE_ATTRIBUTE_NAME)
    public String getPtFareZoneAttributeName() {
        return ptFareZoneAttributeName;
    }


    @StringSetter(PT_FARE_ZONE_ATTRIBUTE_NAME)
    public void setPtFareZoneAttributeName(String attributeName) {
        ptFareZoneAttributeName = attributeName;
    }




    public void setZonesGroup(ZonesGroup zonesGroup) {
        super.addParameterSet(zonesGroup);
        this.zonesGroup = zonesGroup;
    }

    public ZonesGroup getZonesGroup() {
        return zonesGroup;
    }

    public void setFaresGroup(FaresGroup faresGroup) {
        super.addParameterSet(faresGroup);
        this.faresGroup = faresGroup;
    }

    public FaresGroup getFaresGroup() {
        return faresGroup;
    }

    public static class ZonesGroup extends ReflectiveConfigGroup{
        public static final String GROUP_NAME = "zones";
        private static final Set<Zone> zones = new HashSet<>();

        public ZonesGroup() {
            super(GROUP_NAME);
        }

        public void addZone(Zone zone) {
            super.addParameterSet(zone);
        }

        public Set<String> getAllBaseZones(){
            Set<String> allZones = new HashSet<>();
            for(var zone: zones){
                if (!zone.isHybrid){ allZones.add(zone.zoneName); }
            }
            return allZones;
        }

        public Map<String, Set<String>> getAllHybridZones(){
            Map<String, Set<String>> allZones = new HashMap<>();
            for(var zone: zones){
                if (zone.isHybrid){ allZones.put(zone.zoneName, zone.getZoneAssignment()); }
            }
            return allZones;
        }


        public static class Zone extends ReflectiveConfigGroup{
            public static final String GROUP_NAME = "zone";

            public Zone() {
                super(GROUP_NAME);
            }

            public Zone(String zoneName, boolean isHybrid) {
                super(GROUP_NAME);
                setZoneName(zoneName);
                setIsHybrid(isHybrid);
            }

            public Zone(String zoneName, boolean isHybrid, Set<String> zoneAssignment) {
                super(GROUP_NAME);
                setZoneName(zoneName);
                setIsHybrid(isHybrid);
                setZoneAssignment(zoneAssignment);
            }

            private static final String ZONE_NAME = "zoneName";
            private static final String IS_HYBRID = "isHybrid";
            private static final String ZONE_ASSIGNMENT = "zoneAssignment";

            private String zoneName;
            private boolean isHybrid;
            private final Set<String> zoneAssignment = new HashSet<>();

            @StringGetter(ZONE_NAME)
            public String getZoneName(){
                return zoneName;
            }

            @StringSetter(ZONE_NAME)
            public void setZoneName(String zoneName){
                this.zoneName = zoneName;
            }

            @StringGetter(IS_HYBRID)
            public Boolean getIsHybrid(){
                return isHybrid;
            }

            @StringSetter(IS_HYBRID)
            public void setIsHybrid(Boolean isHybrid){
                this.isHybrid = isHybrid;
            }

            @StringGetter(ZONE_ASSIGNMENT)
            private String getDeterministicZoneAssignment() {
                return CollectionUtils.setToString(this.zoneAssignment);
            }

            public Set<String> getZoneAssignment() {
                return this.zoneAssignment;
            }

            @StringSetter(ZONE_ASSIGNMENT)
            private void setZoneAssignment(String modes) {
                setZoneAssignment(CollectionUtils.stringToSet(modes));
            }

            public void setZoneAssignment(Set<String> modes) {
                this.zoneAssignment.clear();
                this.zoneAssignment.addAll(modes);
            }

        }

    }


    public static class FaresGroup extends ReflectiveConfigGroup{
        public static final String GROUP_NAME = "fares";
        private static final String OUT_OF_ZONE_TAG = "outOfZoneTag";
        private static final String OUT_OF_ZONE_PRICE = "outOfZonePrice";

        private static String outOfZoneTag = "out";
        private static double outOfZonePrice = 0.;
        private static final Set<Fare> fares = new HashSet<>();

        public FaresGroup() {
            super(GROUP_NAME);
        }

        @StringGetter(OUT_OF_ZONE_TAG)
        public String getOutOfZoneTag() {
            return outOfZoneTag;
        }

        @StringSetter(OUT_OF_ZONE_TAG)
        public void setOutOfZoneTag(String attributeName) {
            outOfZoneTag = attributeName;
        }

        @StringGetter(OUT_OF_ZONE_PRICE)
        public double getOutOfZonePrice() {
            return outOfZonePrice;
        }

        @StringSetter(OUT_OF_ZONE_PRICE)
        public void setOutOfZonePrice(double price) {
            outOfZonePrice = price;
        }

        public void addFare(Fare fare) {
            super.addParameterSet(fare);
            fares.add(fare);
        }

        public Map<Integer, Double> getAllFares(){
            Map<Integer, Double> allFares = new HashMap<>();
            for(var fare: fares){ allFares.put(fare.getNumberZones(), fare.getTicketPrice()); }
            return allFares;
        }

        public static class Fare extends ReflectiveConfigGroup{
            public static final String GROUP_NAME = "zone";
            private static final String NUMBER_ZONES = "numberZones";
            private static final String TICKET_PRICE = "ticketPrice";

            private int numberZones = 0;
            private double ticketPrice = 0.;

            public Fare() {
                super(GROUP_NAME);
            }

            public Fare(int numberZones, double ticketPrice) {
                super(GROUP_NAME);
                setNumberZones(numberZones);
                setTicketPrice(ticketPrice);
            }

            @StringGetter(NUMBER_ZONES)
            public Integer getNumberZones(){
                return numberZones;
            }

            @StringSetter(NUMBER_ZONES)
            public void setNumberZones(Integer numberZones){
                this.numberZones = numberZones;
            }

            @StringGetter(TICKET_PRICE)
            public Double getTicketPrice(){
                return ticketPrice;
            }

            @StringSetter(TICKET_PRICE)
            public void setTicketPrice(Double ticketPrice){
                this.ticketPrice = ticketPrice;
            }

        }

    }


}
