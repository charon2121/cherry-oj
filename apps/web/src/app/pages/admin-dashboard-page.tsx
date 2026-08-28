import { Container, Section } from '@/components/ui/layout';
import { Heading } from '@/components/ui/typography';

function AdminDashboardPage() {
  return (
    <Container className="max-w-none">
      <Section className="py-10">
        <Heading level={1} size="2xl">
          Dashboard
        </Heading>
      </Section>
    </Container>
  );
}

export { AdminDashboardPage };
