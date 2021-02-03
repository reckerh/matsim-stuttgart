package org.matsim.stuttgart.ptFares;

import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

public class TestConfigGroup extends ReflectiveConfigGroup {

    public static final String GROUP_NAME = "testModule";
    private double doubleField = Double.NaN;
    private ConfigSubgroup configSubgroup = new ConfigSubgroup();

    public TestConfigGroup() {
        super(GROUP_NAME);
    }

    public ConfigGroup createParameterSet(String type) {
        if (ConfigSubgroup.TYPE.equals(type)) {
            return new ConfigSubgroup();
        } else {
            throw new IllegalArgumentException("Unsupported parameterset-type: " + type);
        }
    }

    public void addParameterSet(ConfigGroup cfg) {
        if (cfg instanceof ConfigSubgroup) {
            this.setConfigSubgroup((ConfigSubgroup)cfg);
        } else {
            throw new IllegalArgumentException("Unsupported parameterset: " + cfg.getClass().getName());
        }
    }

    public ConfigSubgroup setConfigSubgroup(ConfigSubgroup cfg) {
        ConfigSubgroup old = configSubgroup;
        this.configSubgroup = cfg;
        super.addParameterSet(cfg);
        return old;
    }

    public ConfigSubgroup getConfigSubgroup() {
        return this.configSubgroup;
    }

    @StringGetter("doubleField")
    public double getDoubleField() {
        return this.doubleField;
    }

    @StringSetter("doubleField")
    public double setDoubleField(double doubleField) {
        final double old = this.doubleField;
        this.doubleField = doubleField;
        return old;
    }


    public static class ConfigSubgroup extends ReflectiveConfigGroup {
        public static final String TYPE = "testSubGroup";
        private int integerField = 0;

        public ConfigSubgroup() {
            super(TYPE);
        }

        @StringGetter("integerField")
        public int getIntegerField() {
            return this.integerField;
        }

        @StringSetter("integerField")
        public int setIntegerField(int integerField) {
            final int old = this.integerField;
            this.integerField = integerField;
            return old;
        }
    }

}
