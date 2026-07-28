variable "region" {
  description = "OCI home region where the Compute instance will be created."
  type        = string
}

variable "tenancy_ocid" {
  description = "OCI tenancy OCID used to discover Availability Domains."
  type        = string
  sensitive   = true

  validation {
    condition     = trimspace(var.tenancy_ocid) != ""
    error_message = "tenancy_ocid must be provided without a default value."
  }
}

variable "compartment_ocid" {
  description = "OCI compartment OCID where all resources will be created."
  type        = string
  sensitive   = true

  validation {
    condition     = trimspace(var.compartment_ocid) != ""
    error_message = "compartment_ocid must be provided without a default value."
  }
}

variable "instance_name" {
  description = "Display name of the single OCI Compute instance."
  type        = string
  default     = "energiai-backend"

  validation {
    condition     = trimspace(var.instance_name) != ""
    error_message = "instance_name must not be empty."
  }
}

variable "shape" {
  description = "Always Free OCI Compute shape. No alternative shape is allowed."
  type        = string
  default     = "VM.Standard.E2.1.Micro"

  validation {
    condition     = var.shape == "VM.Standard.E2.1.Micro"
    error_message = "Only VM.Standard.E2.1.Micro is allowed; no shape fallback is configured."
  }
}

variable "boot_volume_size_in_gbs" {
  description = "Boot volume size in GB. The default 50 GB is within the Always Free allocation."
  type        = number
  default     = 50

  validation {
    condition     = var.boot_volume_size_in_gbs >= 50 && var.boot_volume_size_in_gbs <= 200
    error_message = "boot_volume_size_in_gbs must be between 50 and 200 GB to remain within a single Always Free boot volume allocation."
  }
}

variable "ssh_public_key" {
  description = "OpenSSH public key authorized for the ubuntu user. Private keys are never accepted."
  type        = string
  sensitive   = true

  validation {
    condition = can(regex(
      "^(ssh-(ed25519|rsa|ecdsa(-sha2-nistp(256|384|521))?)|sk-ssh-ed25519@openssh\\.com)\\s+\\S+",
      trimspace(var.ssh_public_key)
    ))
    error_message = "ssh_public_key must be a non-empty OpenSSH public key."
  }
}

variable "ssh_allowed_cidr" {
  description = "Public CIDR permitted to access SSH. A global SSH source is forbidden."
  type        = string

  validation {
    condition = (
      trimspace(var.ssh_allowed_cidr) != "" &&
      can(cidrnetmask(var.ssh_allowed_cidr)) &&
      var.ssh_allowed_cidr != "0.0.0.0/0"
    )
    error_message = "ssh_allowed_cidr must be a valid non-empty CIDR and must not be 0.0.0.0/0."
  }
}

variable "vcn_cidr" {
  description = "CIDR block assigned to the dedicated VCN."
  type        = string
  default     = "10.0.0.0/16"

  validation {
    condition     = can(cidrnetmask(var.vcn_cidr))
    error_message = "vcn_cidr must be a valid CIDR block."
  }
}

variable "subnet_cidr" {
  description = "CIDR block assigned to the public subnet."
  type        = string
  default     = "10.0.1.0/24"

  validation {
    condition     = can(cidrnetmask(var.subnet_cidr))
    error_message = "subnet_cidr must be a valid CIDR block."
  }
}

variable "operating_system" {
  description = "OCI platform image operating system used for image discovery."
  type        = string
  default     = "Canonical Ubuntu"

  validation {
    condition     = var.operating_system == "Canonical Ubuntu"
    error_message = "Only Canonical Ubuntu images are supported by this configuration."
  }
}

variable "operating_system_version" {
  description = "OCI platform image version used for image discovery."
  type        = string
  default     = "24.04"

  validation {
    condition     = var.operating_system_version == "24.04"
    error_message = "Only Ubuntu 24.04 is supported by this configuration."
  }
}

variable "freeform_tags" {
  description = "Additional non-sensitive freeform tags merged with the required project tags."
  type        = map(string)
  default     = {}
}
