# ################################################################# #
#### LOAD LIBRARY AND DEFINE CORE SETTINGS                       ####
# ################################################################# #

library(summarytools)
library(labelled)
library(tidyr)
library(tidyverse)
library(sjmisc) #descriptives
library(dplyr)
library(writexl)
library(ggplot2)
library(sjlabelled)
library(scales)
library(apollo)

### Clear memory
rm(list = ls())

### Initialise code
apollo_initialise()

### Set core controls
apollo_control = list(
  modelName  ="_MT025_230421",
  modelDescr ="Model in WTP space without interactions, with WTP per minute; MT024, but:
  -all non-time and non-cost main effects only exist in interaction with the travel time
  -(alternatively: MT023 but with WTP space reparameteriziation)",
  indivID    ="ID"
)

# ################################################################# #
#### LOAD DATA AND APPLY ANY TRANSFORMATIONS                     ####
# ################################################################# #

load("01_data/d.route.mnl.RData")

### 051d: Add Student marker

#load core data
load("01_data/2022-02-22_CORE_numeric.RData")

#check if d.route.mnl$ID is unique independently without regarding d.route.mnl$stichprobe
length(unique(d.route.mnl$ID)) == length(unique(paste0(d.route.mnl$ID, d.route.mnl$stichprobe)))
length(unique(d.core$ID)) == length(unique(paste0(d.core$ID, d.core$stichprobe)))
#If this is true -> there is no ID that exists in both samples

d.route.mnl <- na.omit(d.route.mnl) #from 1371 to 1365

database <- d.route.mnl
length(unique(d.route.mnl$ID)) # 229 individuals



# ################################################################# #
#### DEFINE MODEL PARAMETERS                                     ####
# ################################################################# #

### Vector of parameters, including any that are kept fixed in estimation
apollo_beta=c(asc_r1     = 0, #Konstante je Alternative
              asc_r2     = 0,
              
              wtp_t     = 0,
              
              wtp_abzu  = 0,
              
              b_kost  = 0,
              
              wtp_mainroad_perM = 0,
              wtp_sideroad_perM = 0,
              
              wtp_keineInfra_perM = 0,
              wtp_streifen_perM = 0, 
              wtp_radweg_perM = 0, 
              wtp_protectedLane_perM = 0, 
              
              wtp_splitt_perM = 0,
              wtp_pflaster_perM = 0,
              wtp_asphalt_perM = 0)

### Vector with names (in quotes) of parameters to be kept fixed at their starting value in apollo_beta, use apollo_beta_fixed = c() if none
apollo_fixed = c("asc_r1", "wtp_mainroad_perM", "wtp_keineInfra_perM", "wtp_splitt_perM")   #Ggf. ändern / an Häufigkeiten ausrichten?

# ################################################################# #
#### GROUP AND VALIDATE INPUTS                                   ####
# ################################################################# #

apollo_inputs = apollo_validateInputs()

# ################################################################# #
#### DEFINE MODEL AND LIKELIHOOD FUNCTION                        ####
# ################################################################# #

apollo_probabilities=function(apollo_beta, apollo_inputs, functionality="estimate"){
  
  ### Attach inputs and detach after function exit
  apollo_attach(apollo_beta, apollo_inputs)
  on.exit(apollo_detach(apollo_beta, apollo_inputs))
  
  ### Create list of probabilities P
  P = list()
  
  
  ### reparameterization
  
  b_abzu = wtp_abzu * b_kost
  
  b_t = wtp_t * b_kost
  
  b_mainroad = wtp_mainroad_perM * b_kost
  b_sideroad = wtp_sideroad_perM * b_kost
  
  b_keineInfra = wtp_keineInfra_perM * b_kost
  b_streifen = wtp_streifen_perM * b_kost
  b_radweg = wtp_radweg_perM * b_kost
  b_protectedLane = wtp_protectedLane_perM * b_kost
  
  b_splitt = wtp_splitt_perM * b_kost
  b_pflaster = wtp_pflaster_perM * b_kost
  b_asphalt = wtp_asphalt_perM * b_kost
  
  
  ### List of utilities: these must use the same names as in mnl_settings, order is irrelevant
  
  
  V = list()
  V[['r1']]  = asc_r1 +
    b_abzu * r1.abzu + 
    b_t * r1.t +
    b_kost * r1.kost + 
    
    b_mainroad * r1.mainroad * r1.t + b_sideroad * r1.sideroad * r1.t + 
    
    b_keineInfra * r1.keineInfra * r1.t + b_streifen * r1.streifen * r1.t + 
    b_radweg * r1.radweg * r1.t + b_protectedLane * r1.protectedLane * r1.t +
    
    b_splitt * r1.splitt * r1.t + b_pflaster * r1.pflaster * r1.t + 
    b_asphalt * r1.asphalt * r1.t
  
  V[['r2']]  = asc_r2 +
    b_abzu * r2.abzu + 
    b_t * r2.t + 
    b_kost * r2.kost + 
    
    b_mainroad * r2.mainroad * r2.t + b_sideroad * r2.sideroad * r2.t + 
    
    b_keineInfra * r2.keineInfra * r2.t + b_streifen * r2.streifen * r2.t + 
    b_radweg * r2.radweg * r2.t + b_protectedLane * r2.protectedLane * r2.t +
    
    b_splitt * r2.splitt * r2.t + b_pflaster * r2.pflaster * r2.t + 
    b_asphalt * r2.asphalt * r2.t
  
  
  
  ### Define settings for MNL model component
  mnl_settings = list(
    alternatives  = c(r1=1, r2=2),
    avail         = list(r1=av_r1, r2=av_r2), # je Alternative ein Dummy generieren = 1 'available',
    choiceVar     = r_choice.n,
    V             = V
  )
  
  ### Compute probabilities using MNL model
  P[['model']] = apollo_mnl(mnl_settings, functionality)
  
  ### Take product across observation for same individual
  P = apollo_panelProd(P, apollo_inputs, functionality)
  
  ### Prepare and return outputs of function
  P = apollo_prepareProb(P, apollo_inputs, functionality)
  return(P)
}

# ################################################################# #
#### MODEL ESTIMATION                                            ####
# ################################################################# #

model = apollo_estimate(apollo_beta, apollo_fixed, apollo_probabilities, apollo_inputs)

# ################################################################# #
#### MODEL OUTPUTS                                               ####
# ################################################################# #

# ----------------------------------------------------------------- #
#---- FORMATTED OUTPUT (TO SCREEN)                               ----
# ----------------------------------------------------------------- #

apollo_modelOutput(model)

# ----------------------------------------------------------------- #
#---- FORMATTED OUTPUT (TO FILE, using model name)               ----
# ----------------------------------------------------------------- #

apollo_saveOutput(model)
###Warning bei output - Problem?



# ################################################################# #
##### ADDITIONAL RESULTS ANALYSIS AND DIAGNOSTICS                ####
# ################################################################# #

### Print the outputs of additional diagnostics to file (comment out sink to file command below if not desired)
### Remember to run the line closing any open sinks at the end of this file
sink(paste(model$apollo_control$modelName,"_additional_output.txt",sep=""),split=TRUE)

# -------------
# ----------------------------------------------------------------- #
#---- LIKELIHOOD RATIO TEST AGAINST BASE MODEL                   ----
# ----------------------------------------------------------------- #

apollo_lrTest("_MT024_230324",model)


# ----------------------------------------------------------------- #
#---- DELTA TESTS OF SPECIFIC PARAMETERS                         ----
# ----------------------------------------------------------------- #


# ################################################################# #
##### CLOSE FILE WRITING                                         ####
# ################################################################# #

# switch off file writing if in use
if(sink.number()>0) sink()


