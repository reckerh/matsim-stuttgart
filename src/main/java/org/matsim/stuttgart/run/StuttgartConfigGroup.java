package org.matsim.stuttgart.run;

import org.matsim.core.config.ReflectiveConfigGroup;

import java.net.URI;
import java.net.URL;

public class StuttgartConfigGroup extends ReflectiveConfigGroup {

    public static String GROUP_NAME = "stuttgart";

    private String dilutionAreaShape;

    @StringSetter("dilutionAreaShape")
    public void setDilutionAreaShape(String path) {
        this.dilutionAreaShape = path;
    }

    @StringGetter("dilutionAreaShape")
    public String getDilutionAreaShape() { return dilutionAreaShape; }

    public StuttgartConfigGroup() {
        super(GROUP_NAME);
    }
}
