output "instance_id" {
  description = "OCID of the single OCI Compute instance."
  value       = oci_core_instance.compute.id
}

output "instance_name" {
  description = "Display name of the single OCI Compute instance."
  value       = oci_core_instance.compute.display_name
}

output "instance_state" {
  description = "Current lifecycle state of the OCI Compute instance."
  value       = oci_core_instance.compute.state
}

output "public_ip" {
  description = "Ephemeral public IPv4 address assigned to the instance VNIC."
  value       = oci_core_instance.compute.public_ip
}

output "private_ip" {
  description = "Private IPv4 address assigned to the instance VNIC."
  value       = oci_core_instance.compute.private_ip
}

output "shape" {
  description = "Fixed Always Free shape used by the instance."
  value       = oci_core_instance.compute.shape
}

output "availability_domain" {
  description = "Availability Domain selected from the tenancy."
  value       = oci_core_instance.compute.availability_domain
}

output "selected_image_id" {
  description = "OCID of the dynamically selected Canonical Ubuntu 24.04 image."
  value       = local.selected_image.id
}

output "selected_image_name" {
  description = "Name of the dynamically selected Canonical Ubuntu 24.04 image."
  value       = local.selected_image.display_name
}

output "expected_architecture" {
  description = "Architecture required by this configuration and expected on the instance."
  value       = "x86_64 / AMD64"
}

output "ssh_command" {
  description = "SSH command for the ubuntu user; keep the private key path local."
  value       = "ssh ubuntu@${oci_core_instance.compute.public_ip}"
}
