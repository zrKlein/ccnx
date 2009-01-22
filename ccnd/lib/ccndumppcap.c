/*
 * Reads in a one or more ccnbs from stdin (e.g. as dumped
 * by ccndump), and writes them to a pcap file. Uses the splitting
 * code from ccn_splitccnb.
 */
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#include <pcap.h>
#include <ccn/ccn.h>

#define LLC_LENGTH 4
#define IP_OFFSET LLC_LENGTH
#define IP_ADDR_LENGTH 4
#define IP_HDR_LENGTH 20
#define IP_LENGTH_OFFSET IP_OFFSET + 2
#define IP_CHKSUM_OFFSET IP_OFFSET + 10
#define IP_SRC_ADDR_OFFSET IP_CHKSUM_OFFSET + 2
#define IP_DEST_ADDR_OFFSET IP_CHKSUM_OFFSET + IP_ADDR_LENGTH

#define UDP_OFFSET IP_OFFSET + IP_HDR_LENGTH
#define UDP_HDR_LENGTH 8
#define UDP_LENGTH_OFFSET UDP_OFFSET + 4
#define UDP_CHKSUM_OFFSET UDP_OFFSET + 6
#define DATA_OFFSET UDP_OFFSET + UDP_HDR_LENGTH
#define MAX_PACKET 65536
#define DEFAULT_SRC_PORT 55555
#define DEFAULT_DEST_PORT 4485

static void
usage(const char *progname)
{
    fprintf(stderr,
            "%s <infile> [<infile> ...]\n"
            "   Reads ccnb blocks from one or more files, and writes them in pcap format\n"
            "   to stdout.\n"
            "   ccnb blocks can be generated by any of the other utility programs.\n",
            progname);
    exit(1);
}

static int
dump_udp_packet(pcap_dumper_t *dump_file, 
                unsigned char *ip_src_addr, /*  ipv4, localhost if NULL */
                unsigned char *ip_dest_addr, /* localhost if NULL */
                unsigned short udp_src_port, /* 55555 if 0 */
                unsigned short udp_dest_port, /* 4485 if 0 */
                const unsigned char *data, size_t data_len) { /* data; could be whole ccnb, could
                                                           just be contents */

    unsigned char pktbuf[MAX_PACKET];
    uint32_t llc_val = PF_INET; // in host byte order

    uint16_t nsrc_port = htons((0 == udp_src_port) ? DEFAULT_SRC_PORT : udp_src_port);
    uint16_t ndest_port = htons((0 == udp_dest_port) ? DEFAULT_DEST_PORT : udp_dest_port);
    uint16_t nudp_len = htons(data_len + UDP_HDR_LENGTH);
    uint16_t nip_len = htons(data_len + UDP_HDR_LENGTH + IP_HDR_LENGTH);

    size_t frame_len = data_len + UDP_HDR_LENGTH + IP_HDR_LENGTH + LLC_LENGTH;
    struct pcap_pkthdr pcap_header;
    
    const unsigned char ipHdr[] = {
        // IP header, localhost to localhost
        0x45, // IPv4, 20 byte header
        0x00, // diff serv field
        0x00, 0x00, // length -- UDP length + 20
        0x1a, 0x62, // identification
        0x00, // flags
        0x00, // fragment offset
        0x40, // TTL (64)
        0x11, // proto (UDP=11)
        0x00, 0x00, // ip checksum (calculate, or leave 0 for validation disabled)
        0x7f, 0x00, 0x00, 0x01, // source, localhost if not overwritten
        0x7f, 0x00, 0x00, 0x01 // dest, localhost if not overwritten
    };

    unsigned char udpHdr[UDP_HDR_LENGTH];
    memset(udpHdr, 0, UDP_HDR_LENGTH);
    
    memcpy(&pktbuf[0], (unsigned char *)&llc_val, LLC_LENGTH);
    memcpy(&pktbuf[IP_OFFSET], ipHdr, IP_HDR_LENGTH);
    memcpy(&pktbuf[IP_LENGTH_OFFSET], &nip_len, 2);
    if (NULL != ip_src_addr) {
        memcpy(&pktbuf[IP_SRC_ADDR_OFFSET], ip_src_addr, IP_ADDR_LENGTH);
    }
    if (NULL != ip_dest_addr) {
        memcpy(&pktbuf[IP_DEST_ADDR_OFFSET], ip_dest_addr, IP_ADDR_LENGTH);
    }

    memcpy(&pktbuf[UDP_OFFSET], &nsrc_port, sizeof(unsigned short));
    memcpy(&pktbuf[UDP_OFFSET + sizeof(unsigned short)], &ndest_port, sizeof(unsigned short));
    memcpy(&pktbuf[UDP_LENGTH_OFFSET], &nudp_len, sizeof(unsigned short));

    memcpy(&pktbuf[DATA_OFFSET], data, data_len);

    pcap_header.len = pcap_header.caplen = frame_len;
    pcap_dump((unsigned char *)dump_file, &pcap_header, &pktbuf[0]);

    if (0 != pcap_dump_flush(dump_file)) {
        fprintf(stderr, "Error flushing pcap dump...\n");
        return -1;
    }
    return 0;
}

static int
process_test(pcap_dumper_t *pcap_out, int content_only,
             unsigned char *ip_src_addr, /*  ipv4, localhost if NULL */
             unsigned char *ip_dest_addr, /* localhost if NULL */
             unsigned short udp_src_port, /* 55555 if 0 */
             unsigned short udp_dest_port, /* 4485 if 0 */
             unsigned char *data, size_t n)
{
    struct ccn_skeleton_decoder skel_decoder = {0};
    struct ccn_skeleton_decoder *d = &skel_decoder;
    struct ccn_parsed_ContentObject content;
    struct ccn_indexbuf *comps = ccn_indexbuf_create();
    const unsigned char * content_value;
    size_t content_length;
    int res = 0;
    size_t s;

retry:
    s = ccn_skeleton_decode(d, data, n);
    if (d->state < 0) {
        res = 1;
        fprintf(stderr, "error state %d after %d of %d chars\n",
                (int)d->state, (int)s, (int)n);
    } else if (s == 0) {
        fprintf(stderr, "nothing to do\n");
    } else {
        if (s < n) {
            if (!content_only) {
                if (dump_udp_packet(pcap_out, ip_src_addr, ip_dest_addr, 
                                    udp_src_port, udp_dest_port, data, s) != 0) {
                    res = 2;
                }
            } else {
                if (ccn_parse_ContentObject(data, s, &content, comps) != 0) {
                    fprintf(stderr, "unable to parse content object\n");
                    res = 1;
                } else if (ccn_content_get_value(data, s, &content, &content_value, &content_length) != 0) {
                    fprintf(stderr, "unable to retrieve content value\n");
                    res = 1;
                } else if (dump_udp_packet(pcap_out, ip_src_addr, ip_dest_addr, 
                                           udp_src_port, udp_dest_port, 
                                           content_value, content_length) != 0) {
                    res = 2;
                }
            }
            /* fprintf(stderr, "resuming at index %d\n", (int)d->index); */
            data += s;
            n -= s;
            if (res != 0) {
                fprintf(stderr, "Error dumping content.\n");
                return res;
            }
            goto retry;
        }
        fprintf(stderr, "\n");
    }
    if (!CCN_FINAL_DSTATE(d->state)) {
        res = 1;
        fprintf(stderr, "incomplete state %d after %d of %d chars\n",
                (int)d->state, (int)s, (int)n);
    } else {
        if (!content_only) {
            if (dump_udp_packet(pcap_out, ip_src_addr, ip_dest_addr, 
                                udp_src_port, udp_dest_port, data, s) != 0) {
                res = 2;
            }
        } else {
            if (ccn_parse_ContentObject(data, s, &content, comps) != 0) {
                fprintf(stderr, "unable to parse content object\n");
                res = 1;
            } else if (ccn_content_get_value(data, s, &content, &content_value, &content_length) != 0) {
                fprintf(stderr, "unable to retrieve content value\n");
                res = 1;
            } else if (dump_udp_packet(pcap_out, ip_src_addr, ip_dest_addr, 
                                       udp_src_port, udp_dest_port, content_value, content_length) != 0) {
                res = 2;
            }
        }

        res = 1;
    }
    return(res);
}

static int
process_fd(pcap_dumper_t *pcap_out, int fd, int content_only,
           unsigned char *ip_src_addr, /*  ipv4, localhost if NULL */
           unsigned char *ip_dest_addr, /* localhost if NULL */
           unsigned short udp_src_port, /* 55555 if 0 */
           unsigned short udp_dest_port /* 4485 if 0 */
    )
{
    unsigned char *buf;
    ssize_t len;
    struct stat s;
    int res = 0;

    res = fstat(fd, &s);
    len = s.st_size;
    buf = (unsigned char *)mmap((void *)NULL, len, PROT_READ, MAP_PRIVATE, fd, 0);
    if (buf == NULL) return (1);
    fprintf(stderr, " <!-- input is %6lu bytes -->\n", (unsigned long)len);
    res |= process_test(pcap_out, content_only,
                        ip_src_addr, ip_dest_addr, udp_src_port, udp_dest_port,
                        buf, len);
    munmap((void *)buf, len);
    return(res);
}

int
main(int argc, char **argv)
{
    pcap_t *pcap = NULL;
    pcap_dumper_t *pcap_out = NULL;
    int fd;
    int i;
    int res = 0;
    
    if (argc < 2) {
        usage(argv[0]);
    }

    pcap = pcap_open_dead(DLT_NULL, MAX_PACKET);
    if (NULL == pcap) {
        fprintf(stderr, "Cannot open pcap descriptor!\n");
        exit(-1);
    }

    pcap_out = pcap_dump_open(pcap, "-");
    if (NULL == pcap_out) {
        fprintf(stderr, "Cannot open output stdout!\n");
        usage(argv[0]);
    }

    for (i = 1; argv[i] != 0; i++) {
        fprintf(stderr, "<!-- Processing %s -->\n", argv[i]);

        fd = open(argv[i], O_RDONLY);
        if (-1 == fd) {
            perror(argv[i]);
            return(1);
        }

        /* DKS -- eventually take IP addresses and ports from command line,
           as well as whether to dump only the ccn content. */
        res |= process_fd(pcap_out, fd, 0, NULL, NULL, 0, 0);
    }

    pcap_dump_close(pcap_out);
    pcap_close(pcap);
    return res;
}
