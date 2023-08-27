library(tidyverse)


MERGESAMEGEOMETRIES <- TRUE
#option to merge geometries that totally overlap (-> have the same core id except for suffixes as r, f, ...)
#is useful for analysis with software not designed for matsim data and for PFs, where I created new links on top of old but both keep existing


#set run-specific names and values
SAMPLEPCT <- 10

NAMEBZFDC <- "stuttgart-DCScoring-baseCase-10pct"
NAMESENSTESTDC <- "stuttgart-DCScoring-sensitivityTestFixed-10pct"
NAMEPFDC <- "stuttgart-DCScoring-bikeFriendly-cont-10pct"

NAMEBZFOLD <- "stuttgart-OldScoring-baseCase-10pct"
NAMESENSTESTOLD <- "stuttgart-OldScoring-sensitivityTestFixed-10pct"
NAMEPFOLD <- "stuttgart-OldScoring-bikeFriendly-cont-10pct"

fileNames <- c(NAMEBZFDC, NAMESENSTESTDC, NAMEPFDC, NAMEBZFOLD, NAMESENSTESTOLD, NAMEPFOLD)
shortNames <- c("BZF_DC", "SensTest_DC", "PF_DC", "BZF_Old", "SensTest_Old", "PF_Old")





##### 0 - read data #####

## read single files into a list separately ##
dataList <- list()

for(i in c(1:length(fileNames))){
  
  dataList[[paste0(shortNames[i], "_car")]] <- read.csv2(paste0(fileNames[i], "/", fileNames[i], ".carLinkCounts.csv"))
  dataList[[paste0(shortNames[i], "_bike")]] <- read.csv2(paste0(fileNames[i], "/", fileNames[i], ".bikeLinkCounts.csv"))
  
}





##### 1 - merge data #####

## merge all counts from reverse- and cycleway-links (i.e. links with same geometries) ##
#(this code-bit should be able to be removed without any dependencies from the rest of the script)
if(MERGESAMEGEOMETRIES){
  temp <- NULL
  for(i in c(1:length(dataList))){
    
    temp <- dataList[[i]]
    
    #delete _cycleway-suffixes
    temp$baseLink <- ifelse(
      endsWith(temp$Link, "_cycleway"),
      str_sub(temp$Link, end = -10),
      temp$Link
    )
    
    #change r-suffixes to f
    temp$baseLink2 <- ifelse(
      endsWith(temp$baseLink, "r"),
      paste0(str_sub(temp$baseLink, end = -2), "f"),
      temp$baseLink
    )
    
    #sum up the counts based on the core links (different for cars and bikes due to diff count var names)
    if(i %% 2 == 1){ #case: car-counts
      temp <- temp %>% 
        group_by(baseLink2) %>% 
        summarise(carCount = sum(carCount))
      
      names(temp) <- c("Link", "carCount")
    }else{ #case: bike-counts
      temp <- temp %>% 
        group_by(baseLink2) %>% 
        summarise(bikeCount = sum(bikeCount))
      
      names(temp) <- c("Link", "bikeCount")
    }
    
    dataList[[i]] <- temp
    temp <- NULL
    
  }
}


## merge all count files into one data frame ##

#prepare data frame using the first list entry
data <- dataList[[1]]
names(data) <- c("Link", names(dataList)[1])
collectedLinks <- data[["Link"]]

#merge the other list entries into the data frame
subFrame <- NULL
for(i in c(2:length(dataList))){
  
  subFrame <- NULL
  
  subFrame <- dataList[[i]]
  names(subFrame) <- c("Link", names(dataList)[i])
  #print(names(subFrame))
  data <- merge(data, subFrame, by = "Link", all = TRUE)
  collectedLinks <- c(collectedLinks, subFrame[["Link"]])
  
}

#test that no links are not included in final data frame
length(data[["Link"]]) == length(unique(collectedLinks))

#set NA counts to 0
for(i in names(data)[c(2:length(names(data)))]){
  data[[i]][which(is.na(data[[i]]))] <- 0
}





##### 2 - calculate relevant differences #####

## define diffs to calculate ##
#(I will calculate 1st entry - (minus) 2nd entry for abs. or 2nd entry / 1st entry for rel. deviations)
diffsToCalc <- rbind(
  c("BZF_Old", "BZF_DC"),
  c("BZF_DC", "SensTest_DC"),
  c("BZF_Old", "SensTest_Old"),
  c("SensTest_Old", "SensTest_DC"),
  c("BZF_DC", "PF_DC"),
  c("BZF_Old", "PF_Old"),
  c("PF_Old", "PF_DC")
)


## calculate the diffs ##
for(i in c(1:dim(diffsToCalc)[1])){
  
  for(j in c("_car", "_bike")){
    
    data[[paste0(
      diffsToCalc[i,1], j, " - ", diffsToCalc[i,2], j, " (abs.)"
    )]] <- data[[paste0(diffsToCalc[i,1], j)]] - data[[paste0(diffsToCalc[i,2], j)]]
    
    data[[paste0(
      diffsToCalc[i,1], j, " / ", diffsToCalc[i,2], j, " (rel., %)"
    )]] <- (data[[paste0(diffsToCalc[i,1], j)]] / data[[paste0(diffsToCalc[i,2], j)]])*100
    
  }
  
}


## write out results ##
write.csv2(data, file = "linkDiffsForVia.csv")





##### 3 - merge with network data #####
library(sf)

## read network data ##
net <- st_read("../input/stuttgart-v3.0/networkAttributesStuttgartV3BikeFriendly.geojson")


## create list of names of count variables ##
countNames <- names(data)[c(2:length(names(data)))]


## adjust names of count data for merging ##
names(data)[1] <- "linkId"


## merge data ##
net <- merge(net, data, by = "linkId", all.x = T)

#set NA values to 0 and Inf values to 999999 (geojson cant handle NA or Inf values)
for(i in countNames){
  net[[i]][which(is.na(net[[i]])==T)] <- 0
  net[[i]][which(net[[i]]==Inf)] <- 999999
}


## write out data ##
#st_write(net, "qgisDiffPlots/NetworkV3BikeFriendly_withCounts.geojson", append = F)


filteredNet <- net %>% 
  filter(within) %>% 
  filter(allowedModes != "[pt]")

st_write(filteredNet, "qgisDiffPlots/NetworkV3BikeFriendly_withCounts_cut.geojson")
