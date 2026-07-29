resource "oci_core_instance" "compute" {
  availability_domain = data.oci_identity_availability_domains.available.availability_domains[0].name
  compartment_id      = var.compartment_ocid
  display_name        = var.instance_name
  shape               = var.shape
  freeform_tags       = local.common_freeform_tags

  source_details {
    source_type             = "image"
    source_id               = local.selected_image.id
    boot_volume_size_in_gbs = var.boot_volume_size_in_gbs
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.public.id
    assign_public_ip = true
    nsg_ids          = [oci_core_network_security_group.compute.id]
    display_name     = "${local.name_prefix}-vnic"
  }

  metadata = {
    ssh_authorized_keys = trimspace(var.ssh_public_key)
  }

  lifecycle {
    precondition {
      condition     = length(data.oci_identity_availability_domains.available.availability_domains) > 0
      error_message = "No Availability Domain was found for the supplied tenancy."
    }

    precondition {
      condition     = length(data.oci_core_images.ubuntu_2404_amd64.images) > 0
      error_message = "No Canonical Ubuntu 24.04 x86_64 image compatible with VM.Standard.E2.1.Micro was found."
    }
  }
}
