data "oci_identity_availability_domains" "available" {
  compartment_id = var.tenancy_ocid
}

data "oci_core_images" "ubuntu_2404_amd64" {
  compartment_id           = var.compartment_ocid
  operating_system         = var.operating_system
  operating_system_version = var.operating_system_version
  shape                    = var.shape
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"

  # VM.Standard.E2.1.Micro is x86_64-only; the shape filter excludes
  # incompatible platform images without relying on unsupported fields.
}
