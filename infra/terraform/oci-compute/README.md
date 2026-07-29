# OCI Compute Always Free — Issue #104

This Terraform configuration provisions exactly one OCI Compute instance for
EnergiAI. It is intentionally limited to SSH access and does not install or
deploy application software.

## Fixed design

| Item | Value |
| --- | --- |
| Shape | `VM.Standard.E2.1.Micro` only |
| Architecture | `x86_64 / AMD64` |
| Image | dynamically discovered Canonical Ubuntu 24.04 |
| Boot volume | 50 GB by default |
| Public IP | one ephemeral address on one VNIC |
| SSH user | `ubuntu` |
| Public ingress | TCP/22 only, from `ssh_allowed_cidr` |

It creates a VCN, managed default security list with no ingress rules, one
public subnet, Internet Gateway, route table, Network Security Group, restricted
SSH rule, egress rule, and one Compute instance. It does not create a Load
Balancer, NAT Gateway, extra volumes, Object Storage, database, domain, DNS
record, HTTPS endpoint, Docker, or application deployment.

The shape is deliberately fixed. If OCI has no capacity for
`VM.Standard.E2.1.Micro`, provisioning fails; Terraform will not select a paid
shape or another architecture.

## Prerequisites

- Terraform 1.6 or newer, before 2.0;
- OCI CLI or another supported local OCI authentication method;
- access to the tenancy home region and a compartment where resources may be created;
- an OpenSSH public/private key pair; only the public key is supplied to Terraform;
- a non-global public CIDR for SSH. `0.0.0.0/0` is rejected.

Configure OCI authentication outside this repository, through the local OCI CLI
profile or supported OCI environment variables. Do not copy OCI config files,
API private keys, fingerprints, or real OCIDs into this directory. Confirm the
tenancy home region in OCI before setting `region`.

## Prepare local variables

Create a private local variables file from the versioned example:

```bash
cd infra/terraform/oci-compute
cp terraform.tfvars.example terraform.tfvars
chmod 600 terraform.tfvars
```

Replace placeholders only in the ignored local file. The example CIDR
`203.0.113.10/32` is a TEST-NET documentation address, not a real source.

Create a key when necessary:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/energiai_oci
```

Place the content of `~/.ssh/energiai_oci.pub` in `ssh_public_key`; never
place the private key in variables, outputs, or Git.

## Terraform workflow

Run from this directory:

```bash
terraform init
terraform fmt -recursive
terraform fmt -check -recursive
terraform validate
terraform plan -out=plan.tfplan
terraform show -no-color plan.tfplan
```

Review the plan before any apply. It must show one Compute instance with the
fixed shape, one VNIC with an ephemeral public IP, and TCP/22 ingress only from
the configured CIDR. It must not show a flexible shape configuration, an
alternative shape, extra volume, Load Balancer, or NAT Gateway.

Terraform state, plans, `.terraform/`, and real `terraform.tfvars` are
ignored. Keep local state secure because it can contain infrastructure metadata.
Remote state is intentionally not configured in this issue, preserving future
OCI Resource Manager compatibility.

After the owner reviews the plan and authorizes creation:

```bash
terraform apply plan.tfplan
terraform output
ssh -i ~/.ssh/energiai_oci ubuntu@<public-ip>
```

On the instance, verify:

```bash
uname -m
cat /etc/os-release
```

Expected architecture is `x86_64`; the operating system must be Canonical
Ubuntu 24.04 LTS.

## Cost, destruction, and troubleshooting

Always Free limits are shared by tenancy resources. Verify allocation and
capacity before applying. This configuration creates one non-flexible eligible
instance, one VNIC, and one boot volume; it has no fallback.

When no longer needed, the owner must deliberately review and run:

```bash
terraform destroy
```

Do not run `apply` or `destroy` from automation for this issue.

- **No matching image:** confirm the home region offers Canonical Ubuntu 24.04
  for the fixed shape; image OCIDs are intentionally not fixed.
- **No capacity:** retry later or review another Availability Domain; do not
  change the shape or architecture.
- **SSH unavailable:** confirm the public CIDR, NSG rule, and `ubuntu` user;
  do not open global SSH access.
- **Authentication failure:** repair external OCI authentication; do not save
  credentials in `.tf` or example files.
