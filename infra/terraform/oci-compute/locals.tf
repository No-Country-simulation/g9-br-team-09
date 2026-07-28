locals {
  name_prefix = "energiai-oci-compute"

  common_freeform_tags = merge(
    {
      project     = "EnergiAI"
      environment = "demo"
      managed_by  = "Terraform"
      issue       = "104"
    },
    var.freeform_tags
  )

  selected_image = data.oci_core_images.ubuntu_2404_amd64.images[0]
}
