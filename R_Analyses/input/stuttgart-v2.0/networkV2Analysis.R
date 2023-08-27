library(tidyverse)
library(sf)
options(scipen=999)

##### Read data #####
t.dat <- read.csv("networkAttributes.csv")
dat <- t.dat


## Look at data ##
head(t.dat)
#There is some loss regarding the coord. accuracy, but as MATSim usually uses cartesian coords,
#those should be in a range <0.1m
table(t.dat$allowedModes)



##### Data preparation #####

## get vector of allowed modes, prepare creation of link geometries ##
dat <- t.dat %>% 
  mutate(allowedModesVector = str_sub(allowedModes, start = 2, end = -2)) %>% 
  mutate(allowedModesVector = str_split(allowedModesVector, ',')) %>% 
  mutate(fromPoint = paste(fromX, fromY, fromZ, sep = ","),
         fromPoint = str_split(fromPoint, ','),
         toPoint = paste(toX, toY, toZ, sep = ","),
         toPoint = str_split(toPoint, ',')) #get all from/to coords into one vector to make the following loop easier


## create geometries of links ##

#Loop to create linestrings (not preferred solution, but surprisingly fast)
res <- list()
temp <- NULL
for (i in c(1:length(dat$linkId))){

  res[[i]] <- st_linestring(
    x = rbind(as.numeric(dat$fromPoint[[i]]), 
              as.numeric(dat$toPoint[[i]])),
    dim = "XYZ"
  )
  
}

#Tests for loop
#temp <- rbind(as.numeric(dat$fromPoint[[1]]),
#              as.numeric(dat$toPoint[[1]]))
#temp
#temp <- st_linestring(x = temp, dim = "XYZ")
#ggplot() + geom_sf(data = temp)

#put linestrings into data frame and add crs 
dat$geometry <- st_sfc(res, crs = 25832)
dat <- st_sf(dat)
rm(res, i, temp)


##read in shapefile for stuttgart
stuttgart <- st_read("lh-stuttgart.shp")




##### Analysis #####


##### 0: overarching analyses #####



##### 0.1: Analyses of modesets #####

#link length per modeset
dat %>% 
  group_by(allowedModes) %>% 
  mutate(linkLength = st_length(geometry)) %>% 
  st_drop_geometry() %>% 
  summarise(lengthPerModeset = sum(linkLength)) %>% 
  ungroup() %>% 
  mutate(propLengthPerModeset = lengthPerModeset/sum(lengthPerModeset))
#PT is always kept separately and will thus not be regardes
#For most of the links, all modes are allowed; but a significant portion does not allow bike (-> probably highways etc.;
#but if you see 0.1.1: not only highways but mostly residential)



##### 0.1.1 Analyses per allowed modeset #####

### cycleway types per allowed modeset ###

#calculate the fraction of cycleway options
#Checked on samples of the (working) plot below, the results are correct
#dplyr actions can probably be shortened
cyclewaysPerMode <- dat %>% 
  group_by(allowedModes, cyclewaytype) %>% 
  mutate(countOfCwType = length(cyclewaytype)) %>% 
  ungroup() %>% 
  group_by(allowedModes) %>% 
  mutate(numOfLinksPerAllMode = length(allowedModes)) %>% 
  ungroup() %>% 
  mutate(fractionOfCwTypePerModeset = countOfCwType/numOfLinksPerAllMode) %>% 
  st_set_geometry(NULL) %>% 
  group_by(allowedModes, cyclewaytype) %>% 
  summarise(fractionOfCwTypePerModeset = round(mean(fractionOfCwTypePerModeset),6),
            numOfCwTypePerModeset = length(cyclewaytype))


ggplot(cyclewaysPerMode)+
  geom_col(aes(x = cyclewaytype, y = fractionOfCwTypePerModeset))+
  facet_wrap(vars(allowedModes))+
  theme_bw() +
  theme(axis.text.x = element_text(angle = 45, hjust = 1, size = 6))
  

#other solution for confirmation (https://rstudio-pubs-static.s3.amazonaws.com/291083_2b0374fddc464ed08b4eb16c95d84075.html): 
ggplot(dat) +
  geom_bar(aes(x = cyclewaytype, y = ..prop.., group = 1), stat = "count") +
  facet_wrap(vars(allowedModes)) +
  theme_bw() +
  theme(axis.text.x = element_text(angle = 45, hjust = 1, size = 6))

table(dat$type[which(dat$allowedModes=="[bike]")])



### street types per allowed modeset ###

#plot
ggplot(dat) +
  geom_bar(aes(x = type, y = ..prop.., group = 1), stat = "count") +
  facet_wrap(vars(allowedModes)) +
  theme_bw() +
  theme(axis.text.x = element_text(angle = 45, hjust = 1, size = 6))
#even exclusive modeset bike has types such as primary etc. -> bikeways next to primary etc.? 
#otherwise incongruous scoring possible (as exclusive bike without car would be scored same as mainroad)

ggplot() +
  geom_sf(data = dat[which(dat$allowedModes=="[bike]" & 
                             dat$type %in% c("primary", "secondary", "tertiary")),],
          colour = "blue") #+
  geom_sf(data = dat[which(!(dat$allowedModes=="[bike]" & 
                             dat$type %in% c("primary", "secondary", "tertiary"))),],
          colour = "black")
sum(st_length(dat[which(dat$allowedModes=="[bike]" & 
                      dat$type %in% c("primary", "secondary", "tertiary")),]))
#Sind aber häufig auch eher wenige Segmente so gekennzeichnet; vergleich zu sonstigem Bike
sum(st_length(dat[which(dat$allowedModes=="[bike]" & 
                      !(dat$type %in% c("primary", "secondary", "tertiary"))),]))
#Mit ca. 3-4% der Gesamtstrecke aber trotzdem nicht so wenig


#link length per type and modeset
dat %>% 
  group_by(allowedModes, type) %>% 
  mutate(linkLength = st_length(geometry)) %>% 
  st_drop_geometry() %>% 
  mutate(lengthPerModesetAndType = sum(linkLength)) %>% 
  ungroup() %>% 
  group_by(allowedModes) %>% 
  mutate(lengthPerModeset = sum(linkLength),
         propLengthPerModesetAndType = lengthPerModesetAndType/lengthPerModeset) %>% 
  group_by(allowedModes, type) %>% 
  summarise(lengthPerModesetAndType = mean(lengthPerModesetAndType),
            propLengthPerModesetAndType = mean(propLengthPerModesetAndType),
            lengthPerModeset = mean(lengthPerModeset)) %>% 
  print(n=50)







##### 1: Analyses of road length for all links where bike is allowed #####

## Compute link length ##
dat$length <- st_length(dat)



##### 1.1: Analysis of road type #####
roadTypeAnalysis <- st_drop_geometry(dat) %>% 
  filter(grepl('bike', allowedModes)) %>% 
  group_by(type) %>% 
  summarise(lengthPerType = sum(length)) %>%  #automatically ungroups last grouping layer and reduces number of variables
  mutate(propLengthPerType = lengthPerType/sum(lengthPerType))


## 1.1.1: Analysis of road type per modeset / bike allowed ##
roadType_modeset <- st_drop_geometry(dat) %>% 
  filter(allowedModes != "[pt]") %>% 
  mutate(bikeAllowed = grepl('bike', allowedModes)) %>% 
  group_by(type, bikeAllowed) %>% 
  summarise(lengthType_Modeset = sum(length)) %>% 
  group_by(bikeAllowed) %>%
  mutate(fractType_Modeset = lengthType_Modeset/sum(lengthType_Modeset)) %>% 
  ungroup() %>% 
  select(!lengthType_Modeset) %>% 
  units::drop_units() %>% 
  pivot_wider(names_from = bikeAllowed, values_from = fractType_Modeset)





##### 1.2: Analysis of road surface #####
roadSurfaceAnalysis <- st_drop_geometry(dat) %>% 
  filter(grepl('bike', allowedModes)) %>% 
  group_by(surface) %>% 
  summarise(lengthPerSurface = sum(length)) %>%  #automatically ungroups last grouping layer and reduces number of variables
  mutate(propLengthPerSurface = lengthPerSurface/sum(lengthPerSurface))


## 1.2.1 Analysis of road surface compared to type ##
roadSurface_Type <- st_drop_geometry(dat) %>% 
  filter(grepl('bike', allowedModes)) %>% 
  group_by(surface, type) %>% 
  summarise(lengthPerSurfaceType = sum(length)) %>%  #automatically ungroups last grouping layer and reduces number of variables
  group_by(surface) %>% #overrides previous groupings as .add is F by default
  mutate(lengthPerSurface = sum(lengthPerSurfaceType),
         propLengthPerSurfaceType = lengthPerSurfaceType/lengthPerSurface) %>% 
  ungroup() %>% 
  arrange(desc(lengthPerSurface))

#transform into crosstable
roadSurface_TypeCrosstab <- roadSurface_Type %>% 
  select(!c(lengthPerSurface, propLengthPerSurfaceType)) %>% 
  units::drop_units() %>% 
  pivot_wider(names_from = type, values_from = lengthPerSurfaceType)

roadSurface_TypePropCrosstab <- roadSurface_Type %>% 
  mutate(fractLengthSurface_Type = lengthPerSurfaceType / sum(lengthPerSurfaceType)) %>% 
  select(!c(lengthPerSurface, propLengthPerSurfaceType, lengthPerSurfaceType)) %>% 
  units::drop_units() %>% 
  pivot_wider(names_from = type, values_from = fractLengthSurface_Type)

#Look at residential tags and non-tags
plotDF <- dat %>% 
  filter(grepl('bike', allowedModes)) %>% 
  filter(grepl('residential', type)) %>% 
  mutate(isLikeAsphalt = surface %in% c("asphalt", "concrete", "concrete:plates",
                                         "paved", "paving_stones"),
         isTaggedButNotAsphalt = surface!="" & isLikeAsphalt==F,
         isNotTagged = surface =="")


ggplot() +
  geom_sf(data = stuttgart) +
  geom_sf(data = plotDF[which(plotDF$isLikeAsphalt == T),], colour = "blue") +
  geom_sf(data = plotDF[which(plotDF$isTaggedButNotAsphalt == T),], colour = "green") +
  geom_sf(data = plotDF[which(plotDF$isNotTagged==T),])
ggsave(filename = "1-2-1_plot_roadSurfaces.png")




###### 1.3: analysis of bike infrastructure ######
cyclewayAnalysis <- st_drop_geometry(dat) %>% 
  filter(grepl('bike', allowedModes)) %>% 
  group_by(cyclewaytype) %>% 
  summarise(lengthPerCwtype = sum(length)) %>%  #automatically ungroups last grouping layer and reduces number of variables
  mutate(propLengthPerCwtype = lengthPerCwtype/sum(lengthPerCwtype))


## 1.3.1 analysis of bike infra in relation to type
roadCwtype_Type <- st_drop_geometry(dat) %>% 
  filter(grepl('bike', allowedModes)) %>% 
  group_by(cyclewaytype, type) %>% 
  summarise(lengthCwtype_Type = sum(length)) %>%  #automatically ungroups last grouping layer and reduces number of variables
  group_by(cyclewaytype) %>% #overrides previous groupings as .add is F by default
  mutate(propLengthPerCwtype_Type = lengthCwtype_Type/sum(lengthCwtype_Type)) %>% 
  ungroup() 

#transform into crosstable
roadCwtype_TypeCrosstab <- roadCwtype_Type %>% 
  select(!c(propLengthPerCwtype_Type)) %>% 
  units::drop_units() %>% 
  pivot_wider(names_from = type, values_from = lengthCwtype_Type)

roadCwtype_TypePropCrosstab <- roadCwtype_Type %>% 
  mutate(fractLengthCwtype_Type = lengthCwtype_Type / sum(lengthCwtype_Type)) %>% 
  select(!c(lengthCwtype_Type, propLengthPerCwtype_Type)) %>% 
  units::drop_units() %>% 
  pivot_wider(names_from = type, values_from = fractLengthCwtype_Type)





##### 2: Cartographic analysis of bike infrastructure in Stuttgart #####
########################################################################
#(should only require code befor 0 to be run to be executed)


##### 2.0: Preparation #####

#filter links that lie in stuttgart (required intersection does not work well with pipes; 
#as intersection would result in leftover long-distance links that disturb the map I will use st_within)
datStutt <- dat 
datStutt$within <- as.vector(st_within(datStutt, stuttgart, sparse = F))
datStutt <- datStutt %>% 
  filter(within) %>% 
  mutate(bikeAllowed = grepl('bike', allowedModes)) %>% #Add indicator if bike is allowed 
  filter(!grepl('pt', allowedModes)) #Filter out pt links

ggplot() +
  geom_sf(data = stuttgart) +
  geom_sf(data = datStutt, colour = "blue") 


## Tag bike infrastructure ##
datStutt <- datStutt %>% 
  mutate(
    infraIsLane = ifelse(
      cyclewaytype %in% c("lane", "yes", "right", "opposite", "left", 
                          "lane;opposite_lane", "both", "cyclestreet", "opposite_lane") #| 
        #lcn=="yes"
        , T, F
    ),
    infraIsPath = ifelse(
      type %in% c("pedestrian", "footway"), T, F
    ),
    infraIsPBL = ifelse(
      cyclewaytype %in% c("track", "track;opposite_track", "track;opposite_lane", "opposite_track") |
        type %in% c("path", "cycleway", "track"), T, F
    )
  )

datStutt <- datStutt %>% 
  mutate(
    infraString = "None",
    infraString = ifelse(infraIsLane, "Lane", infraString),
    infraString = ifelse(infraIsPath, "Path", infraString),
    infraString = ifelse(infraIsPBL, "PBL", infraString)
  )




##### 2.1: Analysis of bike infrastructure #####

#Definition of bike infrastructure for following analysis: lanes, opposite_lanes, opposite_tracks
datStutt <- datStutt %>% 
  mutate(hasBikeInfra = cyclewaytype %in% c("lane", "opposite_lane", "opposite_track"))
#With this definition, I only have 17 links with bike infrastructure within stuttgart; this seems very little, so I assume 
#that quite some bike infrastructure will have been filtered out in the network creation, possibly due to constraints on the 
#type hierarchy level

ggplot() +
  geom_sf(data = stuttgart) +
  geom_sf(data = datStutt, colour = "black") +
  geom_sf(data = datStutt[which(datStutt$hasBikeInfra==T),], colour = "blue")
ggsave(filename = "2-1_plot_bikeInfra_Stuttgart.png")
#Bike infrastructure is barely recognizable in plot -> I will probably have to edit the network builder to rebuild the 
#network and keep bike infrastructure in




##### 2.2: Analysis of road types #####

#Definition of sideroads
datStutt <- datStutt %>% 
  mutate(sideroad = type %in% c("living_street", "residential", "unclassified"))

ggplot() + 
  #geom_sf(data = stuttgart) +
  geom_sf(data = datStutt[which(datStutt$bikeAllowed),], aes(colour = "no bike allowed")) +
  geom_sf(data = datStutt[which(datStutt$bikeAllowed & datStutt$sideroad==F),], aes(colour = "bike allowed, mainroad")) +
  geom_sf(data = datStutt[which(datStutt$bikeAllowed & datStutt$sideroad),], aes(colour = "bike allowed, sideroad")) +
  theme_bw() +
  scale_color_manual(name = "Legend",
                     breaks = c("no bike allowed", "bike allowed, mainroad", "bike allowed, sideroad"),
                     values = c("no bike allowed" = "grey", "bike allowed, mainroad" = "black",
                                "bike allowed, sideroad" = "blue"))
ggsave(filename = "2-2_plot_roadTypes_bikeAllowed.png")
#Most of the net in stuttgart are side roads, maybe an overlay with the bike paths would be interesting





##### 2.3: Analysis of road surfaces #####
table(datStutt$surface)

#define surface types
datStutt <- datStutt %>% 
  mutate(isAsphalt = surface %in% c("asphalt", "concrete", "paved", "paving_stones", "paving_stones:30") |
           (surface == "" & type %in% c("primary", "primary_link", "secondary", "secondary_link", 
                                        "tertiary", "tertiary_link", "residential")),
         isCobbled = surface %in% c("cobblestone", "grass_paver", "sett"),
         isGravel = isAsphalt==F & isCobbled==F)

ggplot() +
  geom_sf(data = datStutt[which(datStutt$isGravel),], aes(colour = "macadam/gravel")) +
  geom_sf(data = datStutt[which(datStutt$isCobbled),], aes(colour = "cobbled")) +
  geom_sf(data = datStutt[which(datStutt$isAsphalt),], aes(colour = "asphalt")) +
  theme_bw() +
  scale_color_manual(name = "Legend",
                     breaks = c("macadam/gravel", "cobbled", "asphalt"),
                     values = c("macadam/gravel" = "grey", "cobbled" = "black", "asphalt" = "blue"))
ggsave(filename = "2-3_roadSurfaces_stuttgart.png")
#Almost only asphalt

