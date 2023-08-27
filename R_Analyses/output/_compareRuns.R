library(tidyverse)
library(sf)
library(tmap)
library(ggplot2)
options(scipen=999)

BZFNAME <- "stuttgart-DCScoring-baseCase-10pct"
PFNAME <- "stuttgart-OldScoring-baseCase-10pct"


##### 0 - read and merge data #####

### read car, bike and bike counting station counts ###
carCountsBZF <- read.csv2(paste0(BZFNAME, "/", BZFNAME, ".carLinkCounts.csv"))
bikeCountsBZF <- read.csv2(paste0(BZFNAME, "/", BZFNAME, ".bikeLinkCounts.csv"))
bikeCountingStationsBZF <- read.csv2(paste0(BZFNAME, "/", BZFNAME, ".bikeCountingStationCounts.csv"))

carCountsPF <- read.csv2(paste0(PFNAME, "/", PFNAME, ".carLinkCounts.csv"))
bikeCountsPF <- read.csv2(paste0(PFNAME, "/", PFNAME, ".bikeLinkCounts.csv"))
bikeCountingStationsPF <- read.csv2(paste0(PFNAME, "/", PFNAME, ".bikeCountingStationCounts.csv"))



### read network ###
#as my scenarios are simple and easy to trace back, I plan on only using the larger networks for comparative analyses
t.net <- read.csv("../input/stuttgart-v3.0/networkAttributesStuttgartV3.csv") #adjust as needed



### read stuttgart shapefile ###
stuttgart <- st_read("../input/stuttgart-v2.0/lh-stuttgart.shp")



### process network data into vector-based modelist and geodata into sf, tag bike infrastructure, tag links that lie in stuttgart ###

#create function to process the network
processNetwork <- function(t.net){
  
  #create mode vector, create from- and to-points
  net <- t.net %>% 
    mutate(allowedModesVector = str_sub(allowedModes, start = 2, end = -2)) %>% 
    mutate(allowedModesVector = str_split(allowedModesVector, ',')) %>% 
    mutate(fromPoint = paste(fromX, fromY, fromZ, sep = ","),
           fromPoint = str_split(fromPoint, ','),
           toPoint = paste(toX, toY, toZ, sep = ","),
           toPoint = str_split(toPoint, ',')) #get all from/to coords into one vector to make the following loop easier
  
  #Loop to create linestrings from to- and from-points (not preferred solution, but surprisingly fast)
  res <- list()
  temp <- NULL
  for (i in c(1:length(net$linkId))){
    
    res[[i]] <- st_linestring(
      x = rbind(as.numeric(net$fromPoint[[i]]), 
                as.numeric(net$toPoint[[i]])),
      dim = "XYZ"
    )
    
  }
  
  #put linestrings into data frame and add crs 
  net$geometry <- st_sfc(res, crs = 25832)
  net <- st_sf(net)
  
  #calculate link lengths
  net$length <- st_length(net)
  
  #tag street type
  net <- net %>% 
    mutate(
      typeIsSideroad = ifelse(
        type %in% c("minor", "unclassified", "residential", "living_street", 
                    "service", "pedestrian", "track", "footway", "path", "cycleway"),
        T, F
      ),
      typeIsMainroad = typeIsSideroad == F
    ) %>% 
    mutate(
      streettypeString = ifelse(typeIsSideroad, "sideroad", "mainroad")
    )
  
  #tag bike infrastructure
  net <- net %>% 
    mutate(
      infraIsLane = ifelse(
        cyclewaytype %in% c("lane", "yes", "right", "opposite", "left", 
                            "lane;opposite_lane", "both", "cyclestreet", "opposite_lane") | 
          lcn=="yes", T, F
      ),
      infraIsPath = ifelse(
        type %in% c("pedestrian", "footway"), T, F
      ),
      infraIsPBL = ifelse(
        cyclewaytype %in% c("track", "track;opposite_track", "track;opposite_lane", "opposite_track") |
          type %in% c("path", "cycleway", "track"), T, F
      )
    ) %>% 
    #determine highest infrastructure category on link
    mutate(
      infraString = "None",
      infraString = ifelse(infraIsLane, "Lane", infraString),
      infraString = ifelse(infraIsPath, "Path", infraString),
      infraString = ifelse(infraIsPBL, "PBL", infraString)
    ) %>% 
    #determine if bikes are allowed on the link
    mutate(
      bikeAllowed = grepl('bike', allowedModes)
    )
  
  #tag road surface
  net <- net %>% 
    mutate(isAsphalt = surface %in% c("excellent", "paved", "asphalt;paved", "asphalt", "concrete", "concrete:lanes", "concrete_plates",
                                      "concrete:plates", "paving_stones", "paving_stones:3", "paving_stones:35", "paving_stones:30",
                                      "asphalt;paving_stones:35") |
             (surface == "" & type %in% c("primary", "primary_link", "secondary", "secondary_link", 
                                          "tertiary", "tertiary_link", "residential", "cycleway")),
           isCobbled = surface %in% c("cobblestone", "cobblestone (bad)", "sett", "grass_paver", 
                                      "cobblestone;flattened", "cobblestone:flattened",
                                      "bricks"),
           isGravel = isAsphalt==F & isCobbled==F) %>% 
    #determine surface as single string
    mutate(
      surfaceString = "macadam",
      surfaceString = ifelse(isCobbled, "cobble", surfaceString),
      surfaceString = ifelse(isAsphalt, "asphalt", surfaceString)
    )
  
  
  
  #return processed network
  return(net)
  
}

#process the network
net <- processNetwork(t.net)

#tag links that lie in Stuttgart
net$isInStutt <- as.vector(st_within(net, stuttgart, sparse = F))

#remove pt links
net <- net %>% 
  filter(!grepl('pt', allowedModes))



### merge data from link counts into network ###
#BZF
net$bikeCountBZF <- bikeCountsBZF$bikeCount[match(net$linkId, bikeCountsBZF$Link)]
net$bikeCountBZF[which(is.na(net$bikeCountBZF))] <- 0
net$carCountBZF <- carCountsBZF$carCount[match(net$linkId, carCountsBZF$Link)]
net$carCountBZF[which(is.na(net$carCountBZF))] <- 0

#PF
net$bikeCountPF <- bikeCountsPF$bikeCount[match(net$linkId, bikeCountsPF$Link)]
net$bikeCountPF[which(is.na(net$bikeCountPF))] <- 0
net$carCountPF <- carCountsPF$carCount[match(net$linkId, carCountsPF$Link)]
net$carCountPF[which(is.na(net$carCountPF))] <- 0



### calculate car and bike count differences between both cases ###
net$bikeCountDiff <- net$bikeCountPF-net$bikeCountBZF
net$carCountDiff <- net$carCountPF-net$carCountBZF



### create empty list for analysis results ###
analysisResults <- list()





##### 1 - Calculate summary statistics for link differences #####

### look at measures of dispersion and central values ###

## bike ##

## all links
summary(net$bikeCountBZF)
sd(net$bikeCountBZF)
summary(net$bikeCountPF)
sd(net$bikeCountPF)
summary(net$bikeCountDiff)
sd(net$bikeCountDiff)

ggplot(net) +
  geom_density(aes(x = bikeCountDiff, colour = "diff")) +
  geom_density(aes(x = bikeCountBZF, colour = "BZF")) +
  geom_density(aes(x = bikeCountPF, colour = "PF"))

net %>% 
  st_drop_geometry() %>% 
  pivot_longer(cols = c(bikeCountDiff, bikeCountBZF, bikeCountPF), names_to = "case") %>% 
  ggplot() +
  geom_violin(aes(x = case, y = value, fill = case)) 


## excluding links which have 0 counts in both cases
summary(net$bikeCountBZF[which(net$bikeCountBZF!=0 | net$bikeCountPF!= 0)])
sd(net$bikeCountBZF[which(net$bikeCountBZF!=0 | net$bikeCountPF!= 0)])
summary(net$bikeCountPF[which(net$bikeCountBZF!=0 | net$bikeCountPF!= 0)])
sd(net$bikeCountPF[which(net$bikeCountBZF!=0 | net$bikeCountPF!= 0)])
summary(net$bikeCountDiff[which(net$bikeCountBZF!=0 | net$bikeCountPF!= 0)])
sd(net$bikeCountDiff[which(net$bikeCountBZF!=0 | net$bikeCountPF!= 0)])

ggplot(net[which(net$bikeCountBZF!=0 | net$bikeCountPF!= 0),]) +
  geom_density(aes(x = bikeCountDiff, colour = "diff")) +
  geom_density(aes(x = bikeCountBZF, colour = "BZF")) +
  geom_density(aes(x = bikeCountPF, colour = "PF"))
#skewed to the right, although still strongly centered on values around 0

#not excluding any values (based on quantiles)
net %>% 
  st_drop_geometry() %>% 
  filter(bikeCountBZF!= 0 | bikeCountPF!= 0) %>% 
  pivot_longer(cols = c(bikeCountDiff, bikeCountBZF, bikeCountPF), names_to = "case") %>% 
  ggplot() +
  geom_violin(aes(x = case, y = value, fill = case)) 

#excluding the outermost 2% (based on quantiles)
net %>% 
  st_drop_geometry() %>% 
  filter(bikeCountBZF!= 0 | bikeCountPF!= 0) %>% 
  mutate(
    bikeCountBZF = ifelse(bikeCountBZF<quantile(bikeCountBZF, probs = c(0.01)) | bikeCountBZF>quantile(bikeCountBZF, probs = c(0.99)),
                          NA, bikeCountBZF),
    bikeCountPF = ifelse(bikeCountPF<quantile(bikeCountPF, probs = c(0.01)) | bikeCountPF>quantile(bikeCountPF, probs = c(0.99)),
                         NA, bikeCountPF),
    bikeCountDiff = ifelse(bikeCountDiff<quantile(bikeCountDiff, probs = c(0.01)) | bikeCountDiff>quantile(bikeCountDiff, probs = c(0.99)),
                           NA, bikeCountDiff)
  ) %>% 
  pivot_longer(cols = c(bikeCountDiff, bikeCountBZF, bikeCountPF), names_to = "case") %>% 
  ggplot() +
  geom_violin(aes(x = case, y = value, fill = case)) 


## car ##

## excluding links which have 0 counts in both cases
summary(net$carCountBZF[which(net$carCountBZF!=0 | net$carCountPF!= 0)])
sd(net$carCountBZF[which(net$carCountBZF!=0 | net$carCountPF!= 0)])
summary(net$carCountPF[which(net$carCountBZF!=0 | net$carCountPF!= 0)])
sd(net$carCountPF[which(net$carCountBZF!=0 | net$carCountPF!= 0)])
summary(net$carCountDiff[which(net$carCountBZF!=0 | net$carCountPF!= 0)])
sd(net$carCountDiff[which(net$carCountBZF!=0 | net$carCountPF!= 0)])

ggplot(net[which(net$carCountBZF!=0 | net$carCountPF!= 0),]) +
  geom_density(aes(x = carCountDiff, colour = "diff")) +
  geom_density(aes(x = carCountBZF, colour = "BZF")) +
  geom_density(aes(x = carCountPF, colour = "PF"))
#skewed to the left(?), although still strongly centered on values around 0

#not excluding any values (based on quantiles)
net %>% 
  st_drop_geometry() %>% 
  filter(carCountBZF!= 0 | carCountPF!= 0) %>% 
  pivot_longer(cols = c(carCountDiff, carCountBZF, carCountPF), names_to = "case") %>% 
  ggplot() +
  geom_violin(aes(x = case, y = value, fill = case)) 

#excluding the outermost 2% (based on quantiles)
net %>% 
  st_drop_geometry() %>% 
  filter(carCountBZF!= 0 | carCountPF!= 0) %>% 
  mutate(
    carCountBZF = ifelse(carCountBZF<quantile(carCountBZF, probs = c(0.01)) | carCountBZF>quantile(carCountBZF, probs = c(0.99)),
                          NA, carCountBZF),
    carCountPF = ifelse(carCountPF<quantile(carCountPF, probs = c(0.01)) | carCountPF>quantile(carCountPF, probs = c(0.99)),
                         NA, carCountPF),
    carCountDiff = ifelse(carCountDiff<quantile(carCountDiff, probs = c(0.01)) | carCountDiff>quantile(carCountDiff, probs = c(0.99)),
                           NA, carCountDiff)
  ) %>% 
  pivot_longer(cols = c(carCountDiff, carCountBZF, carCountPF), names_to = "case") %>% 
  ggplot() +
  geom_violin(aes(x = case, y = value, fill = case)) 




### look at correlation measures ###

## bike ##
cor(net$bikeCountBZF, net$bikeCountPF, method = "pearson")
#excluding links where BZF and PF are 0
temp <- net %>% 
  st_drop_geometry() %>% 
  filter(bikeCountBZF!=0 | bikeCountPF!=0) 
cor(x = temp$bikeCountBZF, y = temp$bikeCountPF, method = "pearson")
rm(temp)


## car ##
cor(net$carCountBZF, net$carCountPF, method = "pearson")
#excluding links where BZF and PF are 0
temp <- net %>% 
  st_drop_geometry() %>% 
  filter(carCountBZF!=0 | carCountPF!=0) 
cor(x = temp$carCountBZF, y = temp$carCountPF, method = "pearson")
rm(temp)





##### 2 - create diffplots #####

### bike ####

#positive and negative deltas plotted and scaled together
ggplot() +
  geom_sf(data = stuttgart) +
  geom_sf(data = net[which(net$isInStutt & net$bikeCountDiff==0),], colour = "black") +
  geom_sf(data = net[which(net$isInStutt & net$bikeCountDiff>0),], colour = "green", aes(size = bikeCountDiff)) +
  geom_sf(data = net[which(net$isInStutt & net$bikeCountDiff<0),], colour = "red", aes(size = bikeCountDiff))
#legend will be complicated and plot does not look good, via may actually be a more viable alternative here

#only positive deltas plotted and scaled
ggplot() +
  geom_sf(data = stuttgart) +
  geom_sf(data = net[which(net$isInStutt & net$bikeCountDiff<=0),], colour = "black") +
  geom_sf(data = net[which(net$isInStutt & net$bikeCountDiff>0),], colour = "green", aes(size = bikeCountDiff)) 

#only negative deltas plotted and scaled
ggplot() +
  geom_sf(data = stuttgart) +
  geom_sf(data = net[which(net$isInStutt & net$bikeCountDiff>=0),], colour = "black") +
  geom_sf(data = net[which(net$isInStutt & net$bikeCountDiff<0),], colour = "red", aes(size = bikeCountDiff))

#positive and negative deltas plotted together but not scaled
ggplot() +
  geom_sf(data = stuttgart) +
  geom_sf(data = net[which(net$isInStutt & net$bikeCountDiff==0),], colour = "black") +
  geom_sf(data = net[which(net$isInStutt & net$bikeCountDiff>0),], colour = "green") +
  geom_sf(data = net[which(net$isInStutt & net$bikeCountDiff<0),], colour = "red")





##### 3 - compare diffs across score-relevant categories (infrastructure etc.) #####
analysis <- aggregate(bikeCountDiff~streettypeString+infraString+surfaceString,
          data = net[which(net$carCountBZF!=0 | net$carCountPF!= 0),], FUN = summary)

